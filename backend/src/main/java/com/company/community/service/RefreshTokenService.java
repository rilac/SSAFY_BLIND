package com.company.community.service;

import com.company.community.domain.RefreshToken;
import com.company.community.domain.User;
import com.company.community.exception.InvalidCredentialsException;
import com.company.community.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

// 🗓️ 2026-06-02: Refresh Token 발급/검증/회전/정리.
// 원문은 클라이언트 쿠키에만, DB엔 SHA-256 해시만 저장한다(DB 유출 시에도 원문 복원 불가).
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256bit opaque 난수
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-ttl:14d}")
    private Duration refreshTtl;

    /** 신규 RT 발급 — DB엔 해시 저장, 쿠키용 원문을 반환한다. */
    @Transactional
    public String issue(User user) {
        String raw = generateRawToken();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .expiresAt(LocalDateTime.now().plus(refreshTtl))
                .build();
        refreshTokenRepository.save(token);
        return raw;
    }

    /**
     * 쿠키 원문으로 유효한 RT 엔티티를 조회한다. 없거나 만료면 InvalidCredentialsException(→401).
     * 만료 행 삭제는 정리 스케줄러에 맡긴다(여기서 삭제하면 호출부 트랜잭션 롤백 시 무의미).
     */
    @Transactional(readOnly = true)
    public RefreshToken findValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidCredentialsException("리프레시 토큰이 없습니다.");
        }
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다."));
        if (token.isExpired(LocalDateTime.now())) {
            throw new InvalidCredentialsException("리프레시 토큰이 만료되었습니다. 다시 로그인해주세요.");
        }
        return token;
    }

    /** 회전 — 기존 RT 삭제 후 동일 유저로 새 RT 발급(원문 반환). 옛 토큰은 즉시 무효화된다. */
    @Transactional
    public String rotate(RefreshToken current) {
        User user = current.getUser();
        refreshTokenRepository.delete(current);
        return issue(user);
    }

    /** 로그아웃(단일 세션) — 제시된 RT만 삭제. 없으면 무시(idempotent). */
    @Transactional
    public void deleteByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(refreshTokenRepository::delete);
    }

    /** 탈퇴/휴면 — 유저의 모든 RT 폐기(H-NEW-2의 상태 재검증과 결합해 완전 무효화). */
    @Transactional
    public void deleteAllForUser(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    /** 만료 정리 스케줄러용 — 자연 만료된 RT 벌크 삭제(삭제 건수 반환). */
    @Transactional
    public int deleteExpired() {
        return refreshTokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    // SHA-256(raw) → 64자 hex. 결정적이라 유니크 제약·조회에 사용 가능.
    private String hash(String raw) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
