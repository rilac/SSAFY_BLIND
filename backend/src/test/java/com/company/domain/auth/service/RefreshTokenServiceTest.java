package com.company.domain.auth.service;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.global.exception.InvalidCredentialsException;
import com.company.global.security.AccessTokenStore;
import com.company.global.util.HashUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

// RefreshTokenService 단위 테스트 — Redis 기반 발급/검증/회전/폐기 + 재사용 탐지
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock private StringRedisTemplate redis;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private SetOperations<String, String> setOps;
    @Mock private AccessTokenStore accessTokenStore;

    @InjectMocks private RefreshTokenService refreshTokenService;

    private final Duration ttl = Duration.ofDays(14);
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTtl", ttl);
        user = User.builder()
                .id(42L)
                .mmUserId("mm-1")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
    }

    @Test
    @DisplayName("issue(rememberMe=true): 원문 반환 + Redis에 해시→userId 저장 + uid 집합 갱신")
    void test_issue_true() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(redis.opsForSet()).willReturn(setOps);

        String raw = refreshTokenService.issue(user, true);

        assertThat(raw).isNotBlank();
        String hash = HashUtils.sha256Hex(raw);
        verify(valueOps).set("rt:" + hash, "42", ttl);
        verify(setOps).add("rt:uid:42", hash);
        verify(redis).expire("rt:uid:42", ttl);
    }

    @Test
    @DisplayName("issue(rememberMe=false): RT를 발급하지 않고 null 반환(Redis 미접근)")
    void test_issue_false() {
        assertThat(refreshTokenService.issue(user, false)).isNull();
        verifyNoInteractions(redis, accessTokenStore);
    }

    @Test
    @DisplayName("findValidUserId: 빈 원문이면 InvalidCredentialsException(Redis 미접근)")
    void test_findValidUserId_빈토큰() {
        assertThatThrownBy(() -> refreshTokenService.findValidUserId("  "))
                .isInstanceOf(InvalidCredentialsException.class);
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("findValidUserId: 유효한 RT면 userId를 반환한다")
    void test_findValidUserId_성공() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn("42");

        assertThat(refreshTokenService.findValidUserId("some-raw")).isEqualTo(42L);
    }

    @Test
    @DisplayName("findValidUserId: 미존재 + tombstone 없음이면 예외(재사용 폐기 없음)")
    void test_findValidUserId_미존재() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(valueOps.get(anyString())).willReturn(null); // rt:·rt:used: 모두 miss

        assertThatThrownBy(() -> refreshTokenService.findValidUserId("some-raw"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(accessTokenStore, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("findValidUserId: 회전된 토큰 재사용(tombstone hit)이면 유저 토큰 전체 폐기 후 예외")
    void test_findValidUserId_재사용_탐지() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(redis.opsForSet()).willReturn(setOps);
        String raw = "stolen-raw";
        String hash = HashUtils.sha256Hex(raw);
        given(valueOps.get("rt:" + hash)).willReturn(null);          // 이미 회전됨
        given(valueOps.get("rt:used:" + hash)).willReturn("42");     // tombstone hit
        given(setOps.members("rt:uid:42")).willReturn(Set.of());     // deleteAllForUser 내부

        assertThatThrownBy(() -> refreshTokenService.findValidUserId(raw))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(accessTokenStore).revokeAllForUser(42L);
    }

    @Test
    @DisplayName("rotate: 기존 RT를 원자적으로 소비(GETDEL) + tombstone 기록 + 새 RT 발급")
    void test_rotate() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(redis.opsForSet()).willReturn(setOps);
        String oldRaw = "old-raw";
        String oldHash = HashUtils.sha256Hex(oldRaw);
        given(valueOps.getAndDelete("rt:" + oldHash)).willReturn("42"); // 원자적 소비 성공

        String newRaw = refreshTokenService.rotate(oldRaw, user);

        assertThat(newRaw).isNotBlank().isNotEqualTo(oldRaw);
        verify(setOps).remove("rt:uid:42", oldHash);
        verify(valueOps).set("rt:used:" + oldHash, "42", ttl); // 재사용 탐지 tombstone
    }

    @Test
    @DisplayName("rotate: 이미 소비된 RT(동시 회전 레이스/재사용)면 유저 토큰 전체 폐기 후 예외")
    void test_rotate_동시소비_레이스() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(redis.opsForSet()).willReturn(setOps);
        String oldRaw = "old-raw";
        String oldHash = HashUtils.sha256Hex(oldRaw);
        given(valueOps.getAndDelete("rt:" + oldHash)).willReturn(null); // 다른 요청이 먼저 소비
        given(setOps.members("rt:uid:42")).willReturn(Set.of());        // deleteAllForUser 내부

        assertThatThrownBy(() -> refreshTokenService.rotate(oldRaw, user))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(accessTokenStore).revokeAllForUser(42L);
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class)); // 새 RT 미발급
    }

    @Test
    @DisplayName("deleteByRawToken: 빈 값은 무시(Redis 미접근)")
    void test_deleteByRawToken_null() {
        refreshTokenService.deleteByRawToken(null);
        verifyNoInteractions(redis);
    }

    @Test
    @DisplayName("deleteByRawToken: 존재하는 RT를 삭제하고 uid 집합에서 제거한다")
    void test_deleteByRawToken() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(redis.opsForSet()).willReturn(setOps);
        String raw = "raw";
        String hash = HashUtils.sha256Hex(raw);
        given(valueOps.get("rt:" + hash)).willReturn("42");

        refreshTokenService.deleteByRawToken(raw);

        verify(redis).delete("rt:" + hash);
        verify(setOps).remove("rt:uid:42", hash);
    }

    @Test
    @DisplayName("deleteAllForUser: 유저의 모든 RT 삭제 + AT 팬텀까지 폐기")
    void test_deleteAllForUser() {
        given(redis.opsForSet()).willReturn(setOps);
        given(setOps.members("rt:uid:42")).willReturn(Set.of("h1"));

        refreshTokenService.deleteAllForUser(42L);

        verify(redis).delete(List.of("rt:h1"));
        verify(redis).delete("rt:uid:42");
        verify(accessTokenStore).revokeAllForUser(42L);
    }
}
