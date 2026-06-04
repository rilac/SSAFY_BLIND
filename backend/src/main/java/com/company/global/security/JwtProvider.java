package com.company.global.security;

import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Component
public class JwtProvider {

    private static final int MIN_SECRET_BYTES = 32; // HS256 = 256bit

    // §5-9: 서명 키를 1회만 생성하고, JWT_SECRET이 256bit 미만이면 기동 시점에 fail-fast.
    // (기존엔 매 호출 Keys.hmacShaKeyFor(secret.getBytes()) → 약한 키는 첫 로그인 시점에야 WeakKeyException)
    private final SecretKey key;

    // 🗓️ 2026-06-02: Access Token 수명을 app.jwt.access-ttl(기본 30m)로 외부화·단축(기존 24h).
    // 짧은 AT는 탈취 노출 창을 줄이고, 재발급은 DB 저장 Refresh Token(/api/auth/refresh)이 담당한다.
    private final long accessTtlMs;

    public JwtProvider(@Value("${jwt.secret}") String secret,
                       @Value("${app.jwt.access-ttl:30m}") Duration accessTtl) {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes (256 bits) for HS256, but was "
                            + keyBytes.length + " bytes. Set a longer JWT_SECRET.");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtlMs = accessTtl.toMillis();
    }

    /**
     * (#1) userId, status, role을 클레임에 담아 JWT 생성
     * role 클레임 추가로 토큰만으로도 권한 식별 가능
     */
    public String generateToken(Long userId, UserStatus status, UserRole role) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("status", status.name())
                .claim("role", role.name())    // (#1) role 클레임 추가
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessTtlMs))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * JWT를 파싱하여 클레임을 반환한다.
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
