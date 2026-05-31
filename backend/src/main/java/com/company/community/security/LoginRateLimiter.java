package com.company.community.security;

import com.company.community.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * C-NEW-2: 로그인 무차별 대입(brute-force) 방어용 인메모리 레이트리밋 + 실패 audit 로깅.
 *
 * <p>IP / loginId 두 키 각각에 대해 "window 동안 maxAttempts회 실패" 초과 시 blockSeconds 동안 차단한다.
 * 설정은 {@code app.login-rate-limit.*}로 외부화(기본 5회 / 300초 윈도 / 600초 차단).
 *
 * <p>구현 메모: ConcurrentHashMap 기반 인메모리라 <b>단일 인스턴스 전제</b>다. 수평 확장(다중 인스턴스)
 * 시에는 Redis 카운터(예: INCR + EXPIRE)로 교체해야 한다. 외부 라이브러리(Bucket4j) 미도입.
 */
@Component
public class LoginRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginRateLimiter.class);
    private static final int MAX_ENTRIES = 10_000; // 메모리 보호용 상한

    private final int maxAttempts;
    private final long windowMs;
    private final long blockMs;

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public LoginRateLimiter(
            @Value("${app.login-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${app.login-rate-limit.window-seconds:300}") long windowSeconds,
            @Value("${app.login-rate-limit.block-seconds:600}") long blockSeconds) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowSeconds * 1000L;
        this.blockMs = blockSeconds * 1000L;
    }

    /** 로그인 시도 전 호출 — IP 또는 loginId가 차단 중이면 429를 던진다. */
    public void checkAllowed(String ip, String loginId) {
        if (isBlocked(ipKey(ip)) || isBlocked(idKey(loginId))) {
            log.warn("로그인 레이트리밋 차단: ip={}, loginId={}", ip, loginId);
            throw new TooManyRequestsException("로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    /** 로그인 실패 시 호출 — IP/loginId 카운트를 올리고 임계 초과 시 차단을 설정한다. */
    public void recordFailure(String ip, String loginId) {
        boolean ipBlocked = increment(ipKey(ip));
        boolean idBlocked = increment(idKey(loginId));
        log.warn("로그인 실패(audit): ip={}, loginId={}, ipBlocked={}, idBlocked={}",
                ip, loginId, ipBlocked, idBlocked);
    }

    /** 로그인 성공 시 호출 — 해당 loginId/IP 카운트를 초기화한다. */
    public void recordSuccess(String ip, String loginId) {
        counters.remove(idKey(loginId));
        counters.remove(ipKey(ip));
    }

    private boolean isBlocked(String key) {
        Counter c = counters.get(key);
        return c != null && c.blockedUntil > System.currentTimeMillis();
    }

    /** 카운트 증가 후 차단 여부 반환. 윈도 만료 시 리셋. */
    private boolean increment(String key) {
        long now = System.currentTimeMillis();
        purgeIfLarge(now);
        Counter c = counters.computeIfAbsent(key, k -> new Counter(now));
        synchronized (c) {
            if (now - c.windowStart > windowMs) {
                c.windowStart = now;
                c.count = 0;
            }
            c.count++;
            if (c.count >= maxAttempts) {
                c.blockedUntil = now + blockMs;
                return true;
            }
            return false;
        }
    }

    /** 맵이 비대해지면 완전히 만료된 항목을 정리(인메모리 누수 방지). */
    private void purgeIfLarge(long now) {
        if (counters.size() <= MAX_ENTRIES) return;
        counters.entrySet().removeIf(e -> {
            Counter c = e.getValue();
            synchronized (c) {
                return c.blockedUntil <= now && (now - c.windowStart) > windowMs;
            }
        });
    }

    private String ipKey(String ip) { return "ip:" + ip; }

    private String idKey(String loginId) { return "id:" + loginId; }

    private static class Counter {
        long windowStart;
        int count;
        long blockedUntil;

        Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
