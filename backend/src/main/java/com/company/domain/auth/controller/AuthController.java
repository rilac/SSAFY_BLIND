package com.company.domain.auth.controller;

import com.company.domain.auth.controller.dto.LoginRequest;
import com.company.domain.auth.controller.dto.LoginResponse;
import com.company.domain.auth.controller.dto.TokenPair;
import com.company.domain.auth.service.AuthService;
import com.company.domain.auth.service.RefreshTokenService;
import com.company.domain.user.controller.dto.UserResponse;
import com.company.domain.user.entity.User;
import com.company.global.exception.InvalidCredentialsException;
import com.company.global.security.LoginRateLimiter;
import com.company.global.util.CookieUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenService refreshTokenService; // 로그인 시 이전 세션 RT 정리
    // (#5) CookieUtils를 @Component로 변경했으므로 인스턴스 주입
    private final CookieUtils cookieUtils;
    private final LoginRateLimiter loginRateLimiter; // C-NEW-2

    /**
     * POST /api/auth/login
     * MM 인증 → Access/Refresh 토큰을 HttpOnly 쿠키 2개로 세팅.
     * C-NEW-2: IP/loginId 단위 레이트리밋 — 차단 중이면 429, 자격증명 실패만 카운트.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Boolean>> login(@Valid @RequestBody LoginRequest request,
                                                      HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        loginRateLimiter.checkAllowed(clientIp, request.getLoginId());

        try {
            LoginResponse result = authService.login(
                    request.getLoginId(), request.getPassword(), request.isRememberMe());
            loginRateLimiter.recordSuccess(clientIp, request.getLoginId());

            // 이전 세션의 RT가 쿠키에 남아있으면 서버측에서 폐기 — 그대로 두면 rememberMe=false
            // 재로그인 후에도 옛 RT로 무음 갱신이 이어지고(R6 위반), 계정 전환 시 30분 뒤
            // refresh가 이전 계정 세션으로 되돌리는 문제가 생긴다.
            String staleRefreshToken = extractCookie(httpRequest, CookieUtils.REFRESH_COOKIE_NAME);
            if (staleRefreshToken != null) {
                refreshTokenService.deleteByRawToken(staleRefreshToken);
            }

            // access_token 쿠키엔 팬텀(해시)만 실린다. R6: rememberMe에 따라 쿠키 종류·RT 발급을 분기.
            //  - 체크: 영속 access 쿠키(maxAge=30m) + refresh 쿠키(14d)
            //  - 미체크: 세션 access 쿠키(브라우저 종료 시 만료) + RT 미발급(잔존 refresh 쿠키도 만료시킴)
            ResponseEntity.BodyBuilder response = ResponseEntity.ok();
            if (result.getRefreshToken() != null) {
                response.header(HttpHeaders.SET_COOKIE, cookieUtils.createAccessCookie(result.getAccessToken()).toString());
                response.header(HttpHeaders.SET_COOKIE, cookieUtils.createRefreshCookie(result.getRefreshToken()).toString());
            } else {
                response.header(HttpHeaders.SET_COOKIE, cookieUtils.createSessionAccessCookie(result.getAccessToken()).toString());
                response.header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredRefreshCookie().toString());
            }
            return response.body(Map.of("isNewUser", result.isNewUser()));
        } catch (InvalidCredentialsException e) {
            // 자격증명 실패만 시도 횟수로 집계(MM 장애 503 등은 사용자 책임 아님 → 미집계)
            loginRateLimiter.recordFailure(clientIp, request.getLoginId());
            throw e;
        }
    }

    /**
     * 🗓️ 2026-06-02: POST /api/auth/refresh
     * refresh_token 쿠키로 새 Access Token 재발급(+RT 회전). 둘 다 쿠키로 갱신.
     * RT가 없거나 무효/만료면 401 + 두 쿠키 만료(프론트는 로그인으로 유도).
     */
    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest httpRequest) {
        String rawRefreshToken = extractCookie(httpRequest, CookieUtils.REFRESH_COOKIE_NAME);
        try {
            TokenPair pair = authService.refresh(rawRefreshToken);
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtils.createAccessCookie(pair.accessToken()).toString())
                    .header(HttpHeaders.SET_COOKIE, cookieUtils.createRefreshCookie(pair.refreshToken()).toString())
                    .build();
        } catch (InvalidCredentialsException e) {
            // 재발급 실패 — 남은 쿠키를 만료시켜 깔끔히 로그아웃 상태로
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredAccessCookie().toString())
                    .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredRefreshCookie().toString())
                    .build();
        }
    }

    // 프록시/로드밸런서 뒤를 고려해 X-Forwarded-For 우선, 없으면 remoteAddr.
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * POST /api/auth/logout
     * 제시된 RT를 Redis에서 삭제 + Access 팬텀을 Redis에서 폐기(서버측 무효화) + 두 쿠키를 maxAge(0)으로 만료.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
        authService.logout(
                extractCookie(httpRequest, CookieUtils.REFRESH_COOKIE_NAME),
                extractCookie(httpRequest, CookieUtils.ACCESS_COOKIE_NAME));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredRefreshCookie().toString())
                .build();
    }

    /**
     * GET /api/auth/me
     * 현재 로그인된 유저 정보 반환 — 프론트 인증 가드용
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }

    // 지정 이름의 쿠키 값 추출 — 없으면 null
    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
