package com.company.community.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * (#5) @Component로 변경 — app.cookie.secure 환경변수를 주입받아 dev/prod 분기
 * static 유틸에서 Spring Bean으로 전환하여 프로파일별 secure 값 자동 반영
 */
@Component
public class CookieUtils {

    public static final String COOKIE_NAME = "jwt";
    private static final long MAX_AGE = 60 * 60 * 24; // 24시간 (초 단위)

    // (#5) application-dev.yml=false(HTTP), application-prod.yml=true(HTTPS)
    @Value("${app.cookie.secure}")
    private boolean secure;

    /**
     * JWT 토큰을 담은 HttpOnly 쿠키 생성
     */
    public ResponseCookie createJwtCookie(String token) {
        return ResponseCookie.from(COOKIE_NAME, token)
                .httpOnly(true)           // JS 접근 불가 → XSS 방어
                .secure(this.secure)      // (#5) 프로파일에 따라 자동 분기
                .path("/")
                .maxAge(MAX_AGE)
                .sameSite("Lax")          // CSRF 기본 방어
                .build();
    }

    /**
     * 로그아웃용 — maxAge(0)으로 브라우저 쿠키 즉시 삭제
     */
    public ResponseCookie createExpiredJwtCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(this.secure)      // (#5) 프로파일에 따라 자동 분기
                .path("/")
                .maxAge(0)                // 즉시 만료
                .sameSite("Lax")
                .build();
    }
}
