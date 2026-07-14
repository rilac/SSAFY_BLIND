package com.company.domain.auth.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 로그인 결과 — accessToken(=팬텀 해시) + refreshToken(원문, rememberMe=false면 null) + 신규 가입 여부.
// 컨트롤러 내부 전달용(클라이언트엔 팬텀/RT는 쿠키로, isNewUser만 바디로 노출). 실제 JWT는 Redis에만 존재.
@Getter
@AllArgsConstructor
public class LoginResponse {

    // 팬텀 토큰(SHA-256(jwt) hex) — 실제 JWT가 아니라 Redis 조회 키.
    private final String accessToken;

    // Refresh Token 원문 — rememberMe=false면 null(미발급).
    private final String refreshToken;

    private final boolean isNewUser;
}
