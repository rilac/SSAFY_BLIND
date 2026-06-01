package com.company.community.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * (#5) @Component로 변경 — app.cookie.secure 환경변수를 주입받아 dev/prod 분기.
 * 🗓️ 2026-06-02: Access/Refresh 토큰 분리 — 쿠키를 access_token / refresh_token 두 개로 나눈다.
 *   - access_token: path=/ (모든 API에 전송), 수명 = app.jwt.access-ttl(기본 30m)
 *   - refresh_token: path=/api/auth (재발급/로그아웃 등 인증 엔드포인트에만 전송), 수명 = app.jwt.refresh-ttl(기본 14d)
 */
@Component
public class CookieUtils {

    public static final String ACCESS_COOKIE_NAME = "access_token";
    public static final String REFRESH_COOKIE_NAME = "refresh_token";

    // RT는 일반 API에 실려나가지 않도록 인증 엔드포인트 경로로 한정한다(전송 노출 최소화).
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    // (#5) application-dev.yml=false(HTTP), application-prod.yml=true(HTTPS)
    @Value("${app.cookie.secure}")
    private boolean secure;

    @Value("${app.jwt.access-ttl:30m}")
    private Duration accessTtl;

    @Value("${app.jwt.refresh-ttl:14d}")
    private Duration refreshTtl;

    /** Access Token 쿠키 — 모든 API에 전송(path=/), 수명은 AT TTL과 일치. */
    public ResponseCookie createAccessCookie(String token) {
        return baseCookie(ACCESS_COOKIE_NAME, token, "/")
                .maxAge(accessTtl.getSeconds())
                .build();
    }

    /** Refresh Token 쿠키 — /api/auth 경로에만 전송, 긴 수명. */
    public ResponseCookie createRefreshCookie(String token) {
        return baseCookie(REFRESH_COOKIE_NAME, token, REFRESH_COOKIE_PATH)
                .maxAge(refreshTtl.getSeconds())
                .build();
    }

    /** 로그아웃/탈퇴/휴면 — access_token 쿠키 즉시 삭제. */
    public ResponseCookie createExpiredAccessCookie() {
        return baseCookie(ACCESS_COOKIE_NAME, "", "/")
                .maxAge(0)
                .build();
    }

    /** 로그아웃/탈퇴/휴면 — refresh_token 쿠키 즉시 삭제(생성 시와 동일 path여야 삭제됨). */
    public ResponseCookie createExpiredRefreshCookie() {
        return baseCookie(REFRESH_COOKIE_NAME, "", REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    // 공통 쿠키 속성 — HttpOnly(XSS 방어) + secure(프로파일 분기) + SameSite=Lax(CSRF 기본 방어)
    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(this.secure)
                .path(path)
                .sameSite("Lax");
    }
}
