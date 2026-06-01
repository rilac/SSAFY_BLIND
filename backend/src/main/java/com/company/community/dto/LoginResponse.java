package com.company.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 🗓️ 2026-06-02: 로그인 결과 — Access/Refresh 원문 + 신규 가입 여부.
// 컨트롤러 내부 전달용(클라이언트엔 토큰은 쿠키로, isNewUser만 바디로 노출).
@Getter
@AllArgsConstructor
public class LoginResponse {

    private final String accessToken;

    private final String refreshToken;

    private final boolean isNewUser;
}
