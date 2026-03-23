package com.company.community.controller;

import com.company.community.domain.User;
import com.company.community.dto.LoginRequest;
import com.company.community.dto.LoginResponse;
import com.company.community.dto.UserResponse;
import com.company.community.service.AuthService;
import com.company.community.util.CookieUtils;
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

    /**
     * POST /api/auth/login
     * MM 인증 → JWT를 HttpOnly 쿠키로 세팅, Body에는 isNewUser만 반환
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Boolean>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse result = authService.login(request.getLoginId(), request.getPassword());

        // ★ CookieUtils로 쿠키 생성 (#2)
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieUtils.createJwtCookie(result.getToken()).toString())
                .body(Map.of("isNewUser", result.isNewUser()));
    }

    /**
     * POST /api/auth/logout
     * 쿠키를 maxAge(0)으로 덮어써서 삭제
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, CookieUtils.createExpiredJwtCookie().toString())
                .build();
    }

    /**
     * GET /api/auth/me
     * ★ 현재 로그인된 유저 정보 반환 — 프론트 인증 가드용 (#9)
     * 쿠키가 없거나 유효하지 않으면 401 (JwtAuthFilter에서 처리)
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }
}
