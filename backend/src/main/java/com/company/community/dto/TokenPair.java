package com.company.community.dto;

// 🗓️ 2026-06-02: 재발급 결과 — Access/Refresh 원문 쌍(컨트롤러에서 쿠키 2개로 세팅)
public record TokenPair(String accessToken, String refreshToken) {
}
