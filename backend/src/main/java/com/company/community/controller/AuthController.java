package com.company.community.controller;

import com.company.community.domain.User;
import com.company.community.dto.LoginRequest;
import com.company.community.dto.LoginResponse;
import com.company.community.dto.UserResponse;
import com.company.community.exception.InvalidCredentialsException;
import com.company.community.security.LoginRateLimiter;
import com.company.community.service.AuthService;
import com.company.community.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    // (#5) CookieUtils를 @Component로 변경했으므로 인스턴스 주입
    private final CookieUtils cookieUtils;
    private final LoginRateLimiter loginRateLimiter; // C-NEW-2

    /**
     * POST /api/auth/login
     * MM 인증 → JWT를 HttpOnly 쿠키로 세팅.
     * C-NEW-2: IP/loginId 단위 레이트리밋 — 차단 중이면 429, 자격증명 실패만 카운트.
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Boolean>> login(@Valid @RequestBody LoginRequest request,
                                                      HttpServletRequest httpRequest) {
        String clientIp = clientIp(httpRequest);
        loginRateLimiter.checkAllowed(clientIp, request.getLoginId());

        try {
            LoginResponse result = authService.login(request.getLoginId(), request.getPassword());
            loginRateLimiter.recordSuccess(clientIp, request.getLoginId());
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookieUtils.createJwtCookie(result.getToken()).toString())
                    .body(Map.of("isNewUser", result.isNewUser()));
        } catch (InvalidCredentialsException e) {
            // 자격증명 실패만 시도 횟수로 집계(MM 장애 503 등은 사용자 책임 아님 → 미집계)
            loginRateLimiter.recordFailure(clientIp, request.getLoginId());
            throw e;
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
     * 쿠키를 maxAge(0)으로 덮어써서 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredJwtCookie().toString())
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
}
