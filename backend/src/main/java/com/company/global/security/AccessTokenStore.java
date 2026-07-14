package com.company.global.security;

import com.company.global.util.HashUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 팬텀 토큰 저장소 — Access JWT를 Redis에 두고, 클라이언트에는 팬텀 = SHA-256(JWT)만 노출한다.
 *
 * <p>매 요청 {@code JwtAuthFilter}가 쿠키의 팬텀으로 이 저장소를 조회해 실제 JWT를 얻어 검증한다.
 * <b>조회된 JWT는 서버 밖으로 절대 나가지 않는다</b>(응답/헤더/쿠키/로그 금지). JWT의 무상태성을
 * 포기한 대가로 서버측 즉시 폐기(로그아웃/차단/휴면)가 가능해진다.
 *
 * <p>키 스키마:
 * <ul>
 *   <li>{@code at:{phantom}} → 서명된 JWT (TTL = access-ttl)</li>
 *   <li>{@code at:uid:{userId}} → 해당 유저의 살아있는 팬텀 집합 (일괄 폐기용, TTL = access-ttl)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AccessTokenStore {

    private static final String AT_PREFIX = "at:";
    private static final String AT_UID_PREFIX = "at:uid:";

    private final StringRedisTemplate redis;

    @Value("${app.jwt.access-ttl:30m}")
    private Duration accessTtl;

    /** JWT를 Redis에 저장하고 팬텀(=SHA-256(jwt) hex)을 반환한다. 쿠키에는 이 팬텀만 실린다. */
    public String store(String jwt, Long userId) {
        String phantom = HashUtils.sha256Hex(jwt);
        redis.opsForValue().set(AT_PREFIX + phantom, jwt, accessTtl);
        String uidKey = AT_UID_PREFIX + userId;
        redis.opsForSet().add(uidKey, phantom);
        redis.expire(uidKey, accessTtl);
        return phantom;
    }

    /** 팬텀 → 실제 JWT(서버 전용). miss면 empty(폐기/만료). */
    public Optional<String> resolve(String phantom) {
        if (phantom == null || phantom.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().get(AT_PREFIX + phantom));
    }

    /** 단일 팬텀 폐기(로그아웃/회전). at:uid 집합의 잔여 멤버는 만료된 키를 가리켜 무해(no-op). */
    public void revoke(String phantom) {
        if (phantom == null || phantom.isBlank()) {
            return;
        }
        redis.delete(AT_PREFIX + phantom);
    }

    /** 유저의 모든 살아있는 팬텀 일괄 폐기(차단/휴면/탈퇴 시 즉시 강제 로그아웃). */
    public void revokeAllForUser(Long userId) {
        String uidKey = AT_UID_PREFIX + userId;
        Set<String> phantoms = redis.opsForSet().members(uidKey);
        if (phantoms != null && !phantoms.isEmpty()) {
            List<String> keys = phantoms.stream().map(p -> AT_PREFIX + p).toList();
            redis.delete(keys);
        }
        redis.delete(uidKey);
    }
}
