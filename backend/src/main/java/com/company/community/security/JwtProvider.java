package com.company.community.security;

import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtProvider {

    @Value("${jwt.secret}")
    private String secret;

    private static final long EXPIRATION_MS = 1000L * 60 * 60 * 24; // 24시간

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
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * JWT를 파싱하여 클레임을 반환한다.
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(secret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
