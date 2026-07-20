package com.company.domain.admin.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// R8: 관리자 2차 인증 — BCrypt 코드 검증 + 15분 마커(fail-closed 포함).
// 실패 횟수 제한은 제거됨(정상 운영자가 자기 계정을 잠그는 문제 > 무차별 대입 위험, BCrypt 비용이 억제 담당).
@ExtendWith(MockitoExtension.class)
class AdminStepUpServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AdminAuditService adminAuditService; // 인증 성공/실패도 감사 대상

    @InjectMocks private AdminStepUpService adminStepUpService;

    private void setHash(String hash) {
        ReflectionTestUtils.setField(adminStepUpService, "accessCodeHash", hash);
    }

    @Test
    @DisplayName("코드 해시 미설정이면 항상 실패(fail-closed) — 어떤 코드도 통과 불가")
    void test_해시미설정_fail_closed() {
        setHash("");

        assertThat(adminStepUpService.verify(1L, "any-code")).isFalse();
        verifyNoInteractions(redis, passwordEncoder);
    }

    @Test
    @DisplayName("올바른 코드면 15분 step-up 마커를 저장한다")
    void test_verify_성공() {
        setHash("$2a$10$hash");
        given(redis.opsForValue()).willReturn(valueOps);
        given(passwordEncoder.matches("correct", "$2a$10$hash")).willReturn(true);

        assertThat(adminStepUpService.verify(1L, "correct")).isTrue();

        verify(valueOps).set("admin:stepup:1", "1", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("틀린 코드면 마커를 저장하지 않고 실패를 반환한다(횟수 제한 없음 — 정상 운영자 잠금 방지)")
    void test_verify_실패() {
        setHash("$2a$10$hash");
        given(passwordEncoder.matches("wrong", "$2a$10$hash")).willReturn(false);

        assertThat(adminStepUpService.verify(1L, "wrong")).isFalse();

        verifyNoInteractions(redis); // 실패 카운터를 쓰지 않으므로 Redis 접근 자체가 없다
    }

    @Test
    @DisplayName("여러 번 틀려도 차단되지 않는다 — 매번 코드 검증이 그대로 수행된다")
    void test_verify_반복실패_차단없음() {
        setHash("$2a$10$hash");
        given(passwordEncoder.matches("wrong", "$2a$10$hash")).willReturn(false);

        for (int i = 0; i < 10; i++) {
            assertThat(adminStepUpService.verify(1L, "wrong")).isFalse();
        }
        // 예외 없이 10회 모두 검증 수행 — 무차별 대입 억제는 BCrypt 연산 비용이 담당한다.
        verify(passwordEncoder, times(10)).matches("wrong", "$2a$10$hash");
    }

    @Test
    @DisplayName("설정값이 BCrypt 형식이 아니면 기동 검증이 ERROR를 남긴다(예외로 앱을 죽이지는 않음)")
    void test_기동검증_잘못된_해시() {
        setHash("\"$2a$10$quoted-hash-would-never-match\""); // 따옴표로 감싼 값 — 실제 사고 사례

        adminStepUpService.validateAccessCodeHash(); // 예외를 던지지 않아야 한다

        setHash(""); // 미설정도 마찬가지
        adminStepUpService.validateAccessCodeHash();
    }

    @Test
    @DisplayName("isVerified: 마커 존재 여부를 반환한다")
    void test_isVerified() {
        given(redis.hasKey("admin:stepup:1")).willReturn(true);

        assertThat(adminStepUpService.isVerified(1L)).isTrue();
    }

    @Test
    @DisplayName("clear: step-up 마커를 즉시 폐기한다")
    void test_clear() {
        adminStepUpService.clear(1L);

        verify(redis).delete("admin:stepup:1");
    }
}
