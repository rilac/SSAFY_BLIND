package com.company.domain.auth.service;

import com.company.domain.user.entity.User;
import com.company.global.exception.InvalidCredentialsException;
import com.company.global.security.AccessTokenStore;
import com.company.global.util.HashUtils;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
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
 *   <li>{@code rt:grace:{sha256(raw)}} → {@code {userId}:{후속 RT 원문}} (회전 유예 마커, TTL = refresh-grace)</li>
 *   <li>{@code rt:grace:uid:{userId}} → 유예 마커가 걸린 구 RT 해시 집합 (일괄 폐기용, TTL = refresh-grace)</li>
 * </ul>
 * RT는 rememberMe=true일 때만 존재하므로, RT가 있으면 곧 "로그인 유지"를 선택한 세션이다.
 *
 * <p><b>회전 유예 창(grace window)</b> — RFC 9700(OAuth 2.0 Security BCP) 권고. 회전 직후 짧은 시간
 * ({@code app.jwt.refresh-grace}) 안에 구 RT가 다시 제시되는 것은 다중 탭/재시도로 인한 <i>경합</i>이지
 * 탈취가 아니다. 이 창 안에서는 세션을 폐기하지 않고 <b>직전 회전이 발급한 바로 그 RT를 재발급</b>해
 * 패밀리에 살아있는 RT가 항상 정확히 하나만 존재하도록 유지한다. 창이 지난 뒤의 재제시만
 * tombstone에 걸려 진짜 탈취로 판정되고 {@link #deleteAllForUser}로 이어진다.
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
    private static final String RT_GRACE_PREFIX = "rt:grace:";
    private static final String RT_GRACE_UID_PREFIX = "rt:grace:uid:";

    // 유예 마커 값 구분자 — 원문은 Base64URL(A-Za-z0-9-_)이라 ':'을 포함하지 않으므로 충돌하지 않는다.
    private static final char GRACE_SEPARATOR = ':';

    private static final String STATUS_ROTATED = "ROTATED";   // 정상 회전(내가 승자)
    private static final String STATUS_REPLAYED = "REPLAYED"; // 유예 창 안의 경합 → 직전 결과 재발급
    private static final String STATUS_REUSED = "REUSED";     // 유예 창 밖의 재사용 → 탈취 판정
    private static final String STATUS_UNKNOWN = "UNKNOWN";   // 만료/미존재 → 단순 401

    /**
     * 회전 1건을 원자적으로 처리하는 Lua 스크립트.
     *
     * <p>Lua는 Redis에서 단일 원자 단위로 실행되므로, "A가 구 RT를 소비했지만 아직 유예 마커를 쓰기
     * 전"이라는 중간 상태를 B가 관측할 수 없다. GETDEL + SETNX 조합으로는 이 창이 남아 B가 경합을
     * 탈취로 오판하므로, 판정 전체를 스크립트 하나에 담는다.
     *
     * <pre>
     * KEYS[1]=rt:{old}  KEYS[2]=rt:uid:{uid}      KEYS[3]=rt:used:{old}
     * KEYS[4]=rt:grace:{old}  KEYS[5]=rt:{new}    KEYS[6]=rt:grace:uid:{uid}
     * ARGV[1]=uid ARGV[2]=oldHash ARGV[3]=newHash ARGV[4]=newRaw ARGV[5]=rtTtlSec ARGV[6]=graceTtlSec
     * </pre>
     */
    private static final String ROTATE_LUA = """
            local owner = redis.call('GET', KEYS[1])
            if owner then
              if owner ~= ARGV[1] then
                return {'MISMATCH'}
              end
              redis.call('DEL', KEYS[1])
              redis.call('SREM', KEYS[2], ARGV[2])
              redis.call('SET', KEYS[3], ARGV[1], 'EX', ARGV[5])
              redis.call('SET', KEYS[5], ARGV[1], 'EX', ARGV[5])
              redis.call('SADD', KEYS[2], ARGV[3])
              redis.call('EXPIRE', KEYS[2], ARGV[5])
              redis.call('SET', KEYS[4], ARGV[1] .. ':' .. ARGV[4], 'EX', ARGV[6])
              redis.call('SADD', KEYS[6], ARGV[2])
              redis.call('EXPIRE', KEYS[6], ARGV[6])
              return {'ROTATED', ARGV[1] .. ':' .. ARGV[4]}
            end
            local graced = redis.call('GET', KEYS[4])
            if graced then
              return {'REPLAYED', graced}
            end
            if redis.call('EXISTS', KEYS[3]) == 1 then
              return {'REUSED'}
            end
            return {'UNKNOWN'}
            """;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> ROTATE_SCRIPT = RedisScript.of(ROTATE_LUA, List.class);

    private final StringRedisTemplate redis;
    private final AccessTokenStore accessTokenStore; // 상태 변경 시 살아있는 AT 팬텀도 함께 폐기

    @Value("${app.jwt.refresh-ttl:14d}")
    private Duration refreshTtl;

    // 회전 유예 창 — 다중 탭 경합만 흡수할 만큼 짧게. 길수록 탈취 탐지가 늦어지므로 초 단위로 유지한다.
    @Value("${app.jwt.refresh-grace:20s}")
    private Duration refreshGrace;

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
     *
     * <p>조회 순서가 곧 판정 우선순위다: 살아있는 RT → 회전 유예 마커(경합) → tombstone(탈취).
     * 회전된 토큰은 tombstone과 유예 마커를 동시에 갖는데, 유예 마커가 살아있는 동안에는 경합으로
     * 보고 통과시키고, 마커가 만료된 뒤 남은 tombstone 히트만 탈취로 간주해 전체 폐기한다.
     */
    public Long findValidUserId(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidCredentialsException("리프레시 토큰이 없습니다.");
        }
        String hash = HashUtils.sha256Hex(rawToken);
        String userIdStr = redis.opsForValue().get(RT_PREFIX + hash);
        if (userIdStr != null) {
            return Long.valueOf(userIdStr);
        }
        // 유예 창 안 — 다중 탭 경합. 폐기하지 않고 통과시키면 rotate()가 직전 회전 결과를 재발급한다.
        String graceValue = redis.opsForValue().get(RT_GRACE_PREFIX + hash);
        if (graceValue != null) {
            int sep = graceValue.indexOf(GRACE_SEPARATOR);
            if (sep > 0) {
                return Long.valueOf(graceValue.substring(0, sep));
            }
        }
        String usedBy = redis.opsForValue().get(RT_USED_PREFIX + hash);
        if (usedBy != null) {
            // 유예 창 밖의 회전 재사용(탈취 의심) — 토큰 패밀리 + 살아있는 AT 전부 폐기
            deleteAllForUser(Long.valueOf(usedBy));
            throw new InvalidCredentialsException("리프레시 토큰이 재사용되어 세션을 폐기했습니다. 다시 로그인해주세요.");
        }
        throw new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다.");
    }

    /**
     * 회전 — 구 RT 소비 · tombstone 기록 · 새 RT 등록 · 유예 마커 기록을 Lua로 원자 실행한다.
     *
     * <p>유예 창 안의 재제시(REPLAYED)는 새 RT를 또 만들지 않고 <b>직전 회전이 발급한 원문 그대로</b>
     * 돌려준다. 형제 RT가 생기지 않으므로 "패밀리에 살아있는 RT는 항상 하나"라는 불변식이 유지된다.
     */
    public String rotate(String rawToken, User user) {
        String oldHash = HashUtils.sha256Hex(rawToken);
        String newRaw = generateRawToken();
        String newHash = HashUtils.sha256Hex(newRaw);
        String userId = String.valueOf(user.getId());

        List<String> keys = List.of(
                RT_PREFIX + oldHash,
                RT_UID_PREFIX + userId,
                RT_USED_PREFIX + oldHash,
                RT_GRACE_PREFIX + oldHash,
                RT_PREFIX + newHash,
                RT_GRACE_UID_PREFIX + userId);

        @SuppressWarnings("unchecked")
        List<String> result = redis.execute(ROTATE_SCRIPT, keys,
                userId, oldHash, newHash, newRaw,
                String.valueOf(refreshTtl.getSeconds()), String.valueOf(refreshGrace.getSeconds()));

        String status = (result == null || result.isEmpty()) ? STATUS_UNKNOWN : result.get(0);
        return switch (status) {
            // ROTATED/REPLAYED 모두 "{uid}:{원문}" 형식이라 파싱 경로가 하나다.
            case STATUS_ROTATED, STATUS_REPLAYED -> extractGraceToken(result.get(1), userId);
            case STATUS_REUSED -> {
                deleteAllForUser(user.getId());
                throw new InvalidCredentialsException("리프레시 토큰이 재사용되어 세션을 폐기했습니다. 다시 로그인해주세요.");
            }
            // UNKNOWN(만료/미존재) · MISMATCH(소유자 불일치, 방어적) — 세션 폐기 없이 단순 401
            default -> throw new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다.");
        };
    }

    /**
     * 로그아웃(단일 세션) — 제시된 RT만 삭제. 없으면 무시(idempotent).
     *
     * <p>유예 창 안의 구 RT로 로그아웃이 들어오면 후속 RT가 살아남아 로그아웃이 무력화되므로,
     * 마커에 담긴 후속 원문까지 함께 폐기한다.
     */
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
        String graceValue = redis.opsForValue().getAndDelete(RT_GRACE_PREFIX + hash);
        if (graceValue != null) {
            int sep = graceValue.indexOf(GRACE_SEPARATOR);
            if (sep > 0) {
                String successorHash = HashUtils.sha256Hex(graceValue.substring(sep + 1));
                redis.delete(RT_PREFIX + successorHash);
                redis.opsForSet().remove(RT_UID_PREFIX + graceValue.substring(0, sep), successorHash);
            }
        }
    }

    /**
     * 탈퇴/휴면/차단 — 유저의 모든 RT + 살아있는 AT 팬텀을 폐기(완전 무효화, 즉시 강제 로그아웃).
     * (H-NEW-2의 DB 상태 재검증과 결합해, Redis가 잠시 죽어도 DB 백스톱으로 차단된다.)
     *
     * <p>유예 마커를 남겨두면 폐기 직후 유예 창 안에 구 RT를 제시해 새 AT를 받아갈 수 있으므로
     * (특히 탈취 탐지 경로에서 치명적) 마커 인덱스를 따라 함께 제거한다.
     */
    public void deleteAllForUser(Long userId) {
        String uidKey = RT_UID_PREFIX + userId;
        Set<String> hashes = redis.opsForSet().members(uidKey);
        if (hashes != null && !hashes.isEmpty()) {
            List<String> keys = hashes.stream().map(h -> RT_PREFIX + h).toList();
            redis.delete(keys);
        }
        redis.delete(uidKey);

        String graceUidKey = RT_GRACE_UID_PREFIX + userId;
        Set<String> graceHashes = redis.opsForSet().members(graceUidKey);
        if (graceHashes != null && !graceHashes.isEmpty()) {
            redis.delete(graceHashes.stream().map(h -> RT_GRACE_PREFIX + h).toList());
        }
        redis.delete(graceUidKey);

        accessTokenStore.revokeAllForUser(userId);
    }

    // "{uid}:{원문}" 형식의 유예 마커 값에서 원문을 꺼낸다. uid가 다르면 조작된 값 → 401.
    private String extractGraceToken(String value, String userId) {
        int sep = (value == null) ? -1 : value.indexOf(GRACE_SEPARATOR);
        if (sep <= 0 || !value.substring(0, sep).equals(userId)) {
            throw new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다.");
        }
        return value.substring(sep + 1);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }
}
