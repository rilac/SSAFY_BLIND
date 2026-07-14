package com.company.domain.auth.service;

import com.company.domain.user.entity.User;
import com.company.global.exception.InvalidCredentialsException;
import com.company.global.security.AccessTokenStore;
import com.company.global.util.HashUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Set;

/**
 * Refresh Token 발급/검증/회전/폐기 — Redis 저장(기존 MySQL refresh_tokens 대체).
 *
 * <p>원문(opaque 난수)은 클라이언트 쿠키에만, Redis엔 SHA-256 해시만 저장한다(유출 대비).
 * TTL이 자연 만료를 처리하므로 별도 정리 스케줄러가 필요 없다.
 *
 * <p>키 스키마:
 * <ul>
 *   <li>{@code rt:{sha256(raw)}} → userId (TTL = refresh-ttl)</li>
 *   <li>{@code rt:uid:{userId}} → 해당 유저의 살아있는 RT 해시 집합 (일괄 폐기용)</li>
 *   <li>{@code rt:used:{sha256(raw)}} → userId (회전 재사용 탐지 tombstone)</li>
 * </ul>
 * RT는 rememberMe=true일 때만 존재하므로, RT가 있으면 곧 "로그인 유지"를 선택한 세션이다.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32; // 256bit opaque 난수
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private static final String RT_PREFIX = "rt:";
    private static final String RT_UID_PREFIX = "rt:uid:";
    private static final String RT_USED_PREFIX = "rt:used:";

    private final StringRedisTemplate redis;
    private final AccessTokenStore accessTokenStore; // 상태 변경 시 살아있는 AT 팬텀도 함께 폐기

    @Value("${app.jwt.refresh-ttl:14d}")
    private Duration refreshTtl;

    /**
     * 신규 RT 발급 — rememberMe=false면 발급하지 않고 {@code null} 반환(R6: 로그인 유지 미선택).
     * true면 Redis에 해시 저장 후 쿠키용 원문을 반환한다.
     */
    public String issue(User user, boolean rememberMe) {
        if (!rememberMe) {
            return null;
        }
        String raw = generateRawToken();
        String hash = HashUtils.sha256Hex(raw);
        redis.opsForValue().set(RT_PREFIX + hash, String.valueOf(user.getId()), refreshTtl);
        String uidKey = RT_UID_PREFIX + user.getId();
        redis.opsForSet().add(uidKey, hash);
        redis.expire(uidKey, refreshTtl);
        return raw;
    }

    /**
     * 쿠키 원문으로 유효한 RT의 userId를 조회한다. 없거나 만료면 InvalidCredentialsException(→401).
     * 이미 회전되어 사라진 토큰이 다시 제시되면(tombstone hit) 탈취로 간주해 해당 유저의 모든 토큰을 폐기한다.
     */
    public Long findValidUserId(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidCredentialsException("리프레시 토큰이 없습니다.");
        }
        String hash = HashUtils.sha256Hex(rawToken);
        String userIdStr = redis.opsForValue().get(RT_PREFIX + hash);
        if (userIdStr == null) {
            String usedBy = redis.opsForValue().get(RT_USED_PREFIX + hash);
            if (usedBy != null) {
                // 회전 재사용(탈취 의심) — 토큰 패밀리 + 살아있는 AT 전부 폐기
                deleteAllForUser(Long.valueOf(usedBy));
                throw new InvalidCredentialsException("리프레시 토큰이 재사용되어 세션을 폐기했습니다. 다시 로그인해주세요.");
            }
            throw new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다.");
        }
        return Long.valueOf(userIdStr);
    }

    /** 회전 — 기존 RT를 원자적으로 소비(+재사용 탐지 tombstone) 후 동일 유저로 새 RT 발급(원문 반환). */
    public String rotate(String rawToken, User user) {
        String hash = HashUtils.sha256Hex(rawToken);
        // GETDEL로 원자적 소비 — 같은 RT로 동시에 두 요청이 들어와도(탈취자+정상 클라이언트 레이스)
        // 한쪽만 회전에 성공한다. null이면 이미 소비된 토큰의 재제시 → 탈취 의심으로 전체 폐기.
        String userIdStr = redis.opsForValue().getAndDelete(RT_PREFIX + hash);
        if (userIdStr == null) {
            deleteAllForUser(user.getId());
            throw new InvalidCredentialsException("리프레시 토큰이 재사용되어 세션을 폐기했습니다. 다시 로그인해주세요.");
        }
        redis.opsForSet().remove(RT_UID_PREFIX + user.getId(), hash);
        // 옛 토큰을 tombstone으로 남겨 재사용(탈취) 탐지. 원 토큰 수명만큼만 유지.
        redis.opsForValue().set(RT_USED_PREFIX + hash, String.valueOf(user.getId()), refreshTtl);
        return issue(user, true); // 회전은 rememberMe=true 함의
    }

    /** 로그아웃(단일 세션) — 제시된 RT만 삭제. 없으면 무시(idempotent). */
    public void deleteByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        String hash = HashUtils.sha256Hex(rawToken);
        String userIdStr = redis.opsForValue().get(RT_PREFIX + hash);
        redis.delete(RT_PREFIX + hash);
        if (userIdStr != null) {
            redis.opsForSet().remove(RT_UID_PREFIX + userIdStr, hash);
        }
    }

    /**
     * 탈퇴/휴면/차단 — 유저의 모든 RT + 살아있는 AT 팬텀을 폐기(완전 무효화, 즉시 강제 로그아웃).
     * (H-NEW-2의 DB 상태 재검증과 결합해, Redis가 잠시 죽어도 DB 백스톱으로 차단된다.)
     */
    public void deleteAllForUser(Long userId) {
        String uidKey = RT_UID_PREFIX + userId;
        Set<String> hashes = redis.opsForSet().members(uidKey);
        if (hashes != null && !hashes.isEmpty()) {
            List<String> keys = hashes.stream().map(h -> RT_PREFIX + h).toList();
            redis.delete(keys);
        }
        redis.delete(uidKey);
        accessTokenStore.revokeAllForUser(userId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
