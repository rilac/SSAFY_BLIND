package com.company.community.util;

import org.springframework.http.ResponseCookie;

/**
 * JWT 쿠키 생성/삭제 로직을 한 곳에서 관리하는 유틸리티.
 * AuthController, OnboardingController 등에서 공통 사용.
 */
public final class CookieUtils {

    public static final String COOKIE_NAME = "jwt";
    private static final long MAX_AGE = 60 * 60 * 24; // 24시간 (초 단위)

    private CookieUtils() {
        // 인스턴스 생성 방지
    }

    /**
     * JWT 토큰을 담은 HttpOnly 쿠키 생성
     */
    public static ResponseCookie createJwtCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)       // JS 접근 불가 → XSS 방어
                .secure(false)        // 개발환경(HTTP), 프로덕션에서는 true로 변경
                .path("/")            // 모든 경로에서 쿠키 전송
                .maxAge(MAX_AGE)
                .sameSite("Lax")      // CSRF 기본 방어
                .build();
    }

    /**
     * 로그아웃용 — maxAge(0)으로 브라우저 쿠키 즉시 삭제
     */
    public static ResponseCookie createExpiredJwtCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)            // 즉시 만료
                .sameSite("Lax")
                .build();
    }
}
