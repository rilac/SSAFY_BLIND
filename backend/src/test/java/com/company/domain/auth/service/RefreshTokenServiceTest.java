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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyList;
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
    private final Duration grace = Duration.ofSeconds(20); // 회전 유예 창
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTtl", ttl);
        ReflectionTestUtils.setField(refreshTokenService, "refreshGrace", grace);
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
        given(valueOps.get("rt:grace:" + hash)).willReturn(null);    // 유예 창은 이미 만료 → 진짜 탈취
        given(valueOps.get("rt:used:" + hash)).willReturn("42");     // tombstone hit
        given(setOps.members("rt:uid:42")).willReturn(Set.of());     // deleteAllForUser 내부

        assertThatThrownBy(() -> refreshTokenService.findValidUserId(raw))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(accessTokenStore).revokeAllForUser(42L);
    }

    @Test
    @DisplayName("findValidUserId: 유예 창 안의 구 RT(경합)는 폐기 없이 userId를 반환한다")
    void test_findValidUserId_유예창_경합() {
        given(redis.opsForValue()).willReturn(valueOps);
        String raw = "raced-raw";
        String hash = HashUtils.sha256Hex(raw);
        given(valueOps.get("rt:" + hash)).willReturn(null);                         // 이미 회전됨
        given(valueOps.get("rt:grace:" + hash)).willReturn("42:successor-raw");     // 유예 마커 hit

        assertThat(refreshTokenService.findValidUserId(raw)).isEqualTo(42L);
        verifyNoInteractions(accessTokenStore); // 탈취 판정 아님 → 전체 폐기 없음
    }

    @Test
    @DisplayName("rotate: 유효한 RT면 Lua로 원자 회전 — 새 원문 반환, 키 6종이 규약대로 전달된다")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void test_rotate() {
        // Lua가 승자 경로를 탄 상황: {ROTATED, "{uid}:{newRaw}"} — newRaw는 서비스가 생성하므로 되돌려준다.
        given(redis.<List<String>>execute(any(), anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .willAnswer(inv -> List.of("ROTATED", inv.getArgument(2) + ":" + inv.getArgument(5)));

        String oldRaw = "old-raw";
        String newRaw = refreshTokenService.rotate(oldRaw, user);

        assertThat(newRaw).isNotBlank().isNotEqualTo(oldRaw);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(), keys.capture(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        String oldHash = HashUtils.sha256Hex(oldRaw);
        assertThat(keys.getValue()).containsExactly(
                "rt:" + oldHash,
                "rt:uid:42",
                "rt:used:" + oldHash,
                "rt:grace:" + oldHash,
                "rt:" + HashUtils.sha256Hex(newRaw),
                "rt:grace:uid:42");
        verifyNoInteractions(accessTokenStore);
    }

    @Test
    @DisplayName("rotate: 유예 창 안의 경합(REPLAYED)이면 폐기 없이 직전 회전이 발급한 RT를 그대로 재발급")
    @SuppressWarnings("unchecked")
    void test_rotate_유예창_경합_재발급() {
        given(redis.<List<String>>execute(any(), anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(List.of("REPLAYED", "42:successor-raw"));

        assertThat(refreshTokenService.rotate("old-raw", user)).isEqualTo("successor-raw");
        verifyNoInteractions(accessTokenStore); // 다중 탭은 세션 탈취가 아니다
    }

    @Test
    @DisplayName("rotate: 유예 창 밖의 재사용(REUSED)이면 종전대로 유저 토큰 전체 폐기 후 예외")
    @SuppressWarnings("unchecked")
    void test_rotate_유예창_밖_재사용() {
        given(redis.<List<String>>execute(any(), anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(List.of("REUSED"));
        given(redis.opsForSet()).willReturn(setOps);
        given(setOps.members("rt:uid:42")).willReturn(Set.of()); // deleteAllForUser 내부

        assertThatThrownBy(() -> refreshTokenService.rotate("old-raw", user))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(accessTokenStore).revokeAllForUser(42L);
    }

    @Test
    @DisplayName("rotate: 만료/미존재(UNKNOWN)면 세션 폐기 없이 401")
    @SuppressWarnings("unchecked")
    void test_rotate_미존재() {
        given(redis.<List<String>>execute(any(), anyList(),
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .willReturn(List.of("UNKNOWN"));

        assertThatThrownBy(() -> refreshTokenService.rotate("old-raw", user))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(accessTokenStore, never()).revokeAllForUser(any());
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
    @DisplayName("deleteByRawToken: 유예 창 안의 구 RT로 로그아웃하면 후속 RT까지 함께 폐기한다")
    void test_deleteByRawToken_유예창_후속토큰_정리() {
        given(redis.opsForValue()).willReturn(valueOps);
        given(redis.opsForSet()).willReturn(setOps);
        String raw = "old-raw";
        String hash = HashUtils.sha256Hex(raw);
        given(valueOps.get("rt:" + hash)).willReturn(null); // 이미 회전됨
        given(valueOps.getAndDelete("rt:grace:" + hash)).willReturn("42:successor-raw");

        refreshTokenService.deleteByRawToken(raw);

        String successorHash = HashUtils.sha256Hex("successor-raw");
        verify(redis).delete("rt:" + successorHash); // 후속 RT가 살아남아 로그아웃이 무력화되면 안 된다
        verify(setOps).remove("rt:uid:42", successorHash);
    }

    @Test
    @DisplayName("deleteAllForUser: 유저의 모든 RT + 유예 마커 + AT 팬텀까지 폐기")
    void test_deleteAllForUser() {
        given(redis.opsForSet()).willReturn(setOps);
        given(setOps.members("rt:uid:42")).willReturn(Set.of("h1"));
        given(setOps.members("rt:grace:uid:42")).willReturn(Set.of("g1"));

        refreshTokenService.deleteAllForUser(42L);

        verify(redis).delete(List.of("rt:h1"));
        verify(redis).delete("rt:uid:42");
        // 마커가 남으면 폐기 직후 유예 창 안에 구 RT를 제시해 새 AT를 받아갈 수 있다
        verify(redis).delete(List.of("rt:grace:g1"));
        verify(redis).delete("rt:grace:uid:42");
        verify(accessTokenStore).revokeAllForUser(42L);
    }
}
