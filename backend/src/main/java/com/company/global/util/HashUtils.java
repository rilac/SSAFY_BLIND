package com.company.global.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 단방향 해시 유틸 — SHA-256(raw) → 64자 hex.
 *
 * <p>결정적(deterministic)이라 Redis 키·조회에 그대로 쓸 수 있다. 두 곳에서 공유한다:
 * Refresh Token 원문 해시({@code RefreshTokenService})와 팬텀 토큰 = SHA-256(JWT)({@code AccessTokenStore}).
 * preimage가 고엔트로피 값(난수/서명 JWT)이라 salt는 불필요하다.
 */
public final class HashUtils {

    private HashUtils() {}

    public static String sha256Hex(String raw) {
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
