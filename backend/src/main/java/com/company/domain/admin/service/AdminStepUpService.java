package com.company.domain.admin.service;

import com.company.global.exception.TooManyRequestsException;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * R8: 관리자 페이지 2차 인증(step-up).
 *
 * <p>지정된 단일 관리자 코드(BCrypt 해시로 설정, {@code app.admin.access-code-hash})를 검증하고,
 * 성공하면 Redis에 {@code admin:stepup:{userId}} 마커를 15분 TTL로 저장한다. 그 동안만 관리자 API 접근을 허용한다.
 * 코드 해시가 미설정이면 항상 실패(fail-closed) — 운영에서 반드시 설정해야 한다.
 */
@Service
@RequiredArgsConstructor
public class AdminStepUpService {

    private static final String KEY_PREFIX = "admin:stepup:";
    private static final Duration TTL = Duration.ofMinutes(15);

    // 코드 무차별 대입 방어 — 유저당 실패 5회/10분 초과 시 429(윈도 종료까지 차단).
    // 세션 탈취자가 BCrypt 속도만 믿고 코드를 열거하는 것을 막는다(성공 시 카운터 리셋).
    private static final String FAIL_PREFIX = "admin:stepup:fail:";
    private static final int MAX_FAILURES = 5;
    private static final Duration FAIL_WINDOW = Duration.ofMinutes(10);

    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.access-code-hash}")
    private String accessCodeHash;

    /** 코드 검증 성공 시 step-up 마커 저장(15분). 성공 여부 반환. 실패 누적 시 429. */
    public boolean verify(Long userId, String code) {
        if (accessCodeHash == null || accessCodeHash.isBlank()) {
            return false; // 미설정 → fail-closed
        }
        String failKey = FAIL_PREFIX + userId;
        String failures = redis.opsForValue().get(failKey);
        if (failures != null && Integer.parseInt(failures) >= MAX_FAILURES) {
            throw new TooManyRequestsException("인증 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
        if (code == null || !passwordEncoder.matches(code, accessCodeHash)) {
            Long count = redis.opsForValue().increment(failKey);
            if (count != null && count == 1L) {
                redis.expire(failKey, FAIL_WINDOW);
            }
            return false;
        }
        redis.delete(failKey);
        redis.opsForValue().set(KEY_PREFIX + userId, "1", TTL);
        return true;
    }

    /** 현재 유저가 step-up 인증 상태(15분 이내)인지. */
    public boolean isVerified(Long userId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + userId));
    }

    /** step-up 마커 즉시 폐기(로그아웃/차단 시). */
    public void clear(Long userId) {
        redis.delete(KEY_PREFIX + userId);
    }
}
