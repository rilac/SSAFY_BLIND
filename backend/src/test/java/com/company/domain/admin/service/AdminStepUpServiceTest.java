package com.company.domain.admin.service;

import com.company.global.exception.TooManyRequestsException;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// R8: 관리자 2차 인증 — BCrypt 코드 검증 + 15분 마커 + 실패 누적 차단(fail-closed 포함)
@ExtendWith(MockitoExtension.class)
class AdminStepUpServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private PasswordEncoder passwordEncoder;

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
    @DisplayName("올바른 코드면 15분 step-up 마커를 저장하고 실패 카운터를 리셋한다")
    void test_verify_성공() {
        setHash("$2a$10$hash");
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.get("admin:stepup:fail:1")).willReturn(null);
        given(passwordEncoder.matches("correct", "$2a$10$hash")).willReturn(true);

        assertThat(adminStepUpService.verify(1L, "correct")).isTrue();

        verify(redis).delete("admin:stepup:fail:1");
        verify(valueOps).set("admin:stepup:1", "1", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("틀린 코드면 실패를 반환하고 실패 카운터를 올린다(최초 실패 시 윈도 TTL 설정)")
    void test_verify_실패_카운터() {
        setHash("$2a$10$hash");
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.get("admin:stepup:fail:1")).willReturn(null);
        given(passwordEncoder.matches("wrong", "$2a$10$hash")).willReturn(false);
        given(valueOps.increment("admin:stepup:fail:1")).willReturn(1L);

        assertThat(adminStepUpService.verify(1L, "wrong")).isFalse();

        verify(redis).expire("admin:stepup:fail:1", Duration.ofMinutes(10));
        verify(valueOps, never()).set(any(), any(), any(Duration.class)); // 마커 미저장
    }

    @Test
    @DisplayName("실패 5회 누적 후에는 코드 검증 없이 429를 던진다(무차별 대입 차단)")
    void test_verify_누적차단_429() {
        setHash("$2a$10$hash");
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.get("admin:stepup:fail:1")).willReturn("5");

        assertThatThrownBy(() -> adminStepUpService.verify(1L, "whatever"))
                .isInstanceOf(TooManyRequestsException.class);
        verifyNoInteractions(passwordEncoder);
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
