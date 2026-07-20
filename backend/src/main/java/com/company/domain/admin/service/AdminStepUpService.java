package com.company.domain.admin.service;

import com.company.domain.admin.entity.AdminAuditAction;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * R8: 관리자 페이지 2차 인증(step-up).
 *
 * <p>지정된 단일 관리자 코드(BCrypt 해시로 설정, {@code app.admin.access-code-hash})를 검증하고,
 * 성공하면 Redis에 {@code admin:stepup:{userId}} 마커를 15분 TTL로 저장한다. 그 동안만 관리자 API 접근을 허용한다.
 * 코드 해시가 미설정이면 항상 실패(fail-closed) — 운영에서 반드시 설정해야 한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AdminStepUpService {

    private static final String KEY_PREFIX = "admin:stepup:";
    private static final Duration TTL = Duration.ofMinutes(15);

    // 설정된 값이 실제 BCrypt 해시인지 기동 시 확인하기 위한 패턴($2a/$2b/$2y + cost + 53자 salt·hash).
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$.{53}$");

    // 실패 횟수 제한(유저당 5회/10분 → 429)은 제거했다.
    // 이유: BCrypt 검증 자체가 요청당 수십~수백 ms라 무차별 대입 속도가 이미 구조적으로 제한되고,
    // 코드가 단일 공용 값이라 잠금이 걸리면 정상 운영자까지 10분간 모더레이션을 못 하게 된다.
    // (실제로 설정 오류를 디버깅하던 운영자가 자기 계정을 잠그는 일이 발생했다.)

    private final StringRedisTemplate redis;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService adminAuditService;

    @Value("${app.admin.access-code-hash}")
    private String accessCodeHash;

    /**
     * 기동 시 설정된 값이 실제 BCrypt 해시인지 검증한다.
     *
     * <p>BCryptPasswordEncoder는 형식이 틀린 해시를 받으면 WARN 한 줄만 남기고 matches()가 무조건 false를
     * 반환한다. 즉 잘못 설정하면 "코드를 아무리 정확히 입력해도 계속 실패"하는데 원인이 어디에도 드러나지 않는다.
     * 실제로 평문을 넣거나 해시를 따옴표로 감싼 설정 때문에 인증이 구조적으로 불가능한 사고가 있었다.
     * 앱 전체를 죽이지는 않되(관리자 전용 기능이므로), 기동 로그에서 즉시 눈에 띄도록 ERROR로 남긴다.
     */
    @PostConstruct
    void validateAccessCodeHash() {
        if (accessCodeHash == null || accessCodeHash.isBlank()) {
            log.error("[관리자 2차 인증] app.admin.access-code-hash 미설정 — 관리자 인증이 항상 실패합니다(fail-closed).");
            return;
        }
        if (BCRYPT_PATTERN.matcher(accessCodeHash).matches()) {
            // 정상일 때도 남긴다 — 설정이 제대로 들어왔는지 기동 로그 한 줄로 확인할 수 있어야 한다.
            log.info("[관리자 2차 인증] 관리자 코드 해시 로드 완료 (BCrypt 형식 정상, 길이 {}).", accessCodeHash.length());
            return;
        }
        // 값 자체(해시)는 남기지 않되, 무엇이 잘못됐는지 특정할 수 있는 형태 정보만 노출한다.
        // $ 개수가 3이 아니면 .env 로더가 변수 확장으로 $2a/$10 을 먹은 것이다(따옴표 유무와 무관하게 깨짐).
        long dollars = accessCodeHash.chars().filter(c -> c == '$').count();
        log.error("[관리자 2차 인증] app.admin.access-code-hash 가 BCrypt 형식이 아닙니다 — 관리자 인증이 항상 실패합니다. "
                        + "진단: 길이={} (정상 60), 앞4자=[{}] (정상 \"$2a$\"), $개수={} (정상 3), 따옴표시작={}. "
                        + "$개수가 3보다 적으면 .env 로더가 $2a/$10 을 변수로 확장한 것이므로 해시를 .env가 아닌 "
                        + "실행 구성의 환경변수로 직접 넣거나 확장을 막아야 합니다. "
                        + "해시 생성: ./gradlew test --tests com.company.GenerateAdminCodeHashTest -Dadmin.code=\"코드\" --rerun-tasks",
                accessCodeHash.length(),
                accessCodeHash.substring(0, Math.min(4, accessCodeHash.length())),
                dollars,
                accessCodeHash.startsWith("\""));
    }

    /** 코드 검증 성공 시 step-up 마커 저장(15분). 성공 여부 반환. */
    public boolean verify(Long userId, String code) {
        if (accessCodeHash == null || accessCodeHash.isBlank()) {
            return false; // 미설정 → fail-closed
        }
        if (code == null || !passwordEncoder.matches(code, accessCodeHash)) {
            adminAuditService.record(userId, AdminAuditAction.STEP_UP_FAILURE, null);
            return false;
        }
        redis.opsForValue().set(KEY_PREFIX + userId, "1", TTL);
        adminAuditService.record(userId, AdminAuditAction.STEP_UP_SUCCESS, null);
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
