package com.company.community.service;

import com.company.community.domain.RefreshToken;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.exception.InvalidCredentialsException;
import com.company.community.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 🗓️ 2026-06-02: RefreshTokenService 단위 테스트 — 발급/검증/회전/정리
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        // @Value 미주입 → 수명을 리플렉션으로 설정
        ReflectionTestUtils.setField(refreshTokenService, "refreshTtl", Duration.ofDays(14));
        user = User.builder()
                .mmUserId("mm-1")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
    }

    @Test
    @DisplayName("issue: 원문을 반환하고 DB엔 해시(64자 hex)·미래 만료로 저장한다")
    void test_issue_저장_및_해시() {
        String raw = refreshTokenService.issue(user);

        assertThat(raw).isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        // 원문이 아닌 SHA-256 해시(64자 hex)가 저장되어야 한다
        assertThat(saved.getTokenHash())
                .isNotEqualTo(raw)
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
        assertThat(saved.getUser()).isSameAs(user);
    }

    @Test
    @DisplayName("findValid: 원문이 없으면 InvalidCredentialsException")
    void test_findValid_빈토큰_예외() {
        assertThatThrownBy(() -> refreshTokenService.findValid("  "))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    @DisplayName("findValid: 해시 미일치(미존재)면 InvalidCredentialsException")
    void test_findValid_미존재_예외() {
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.findValid("some-raw"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("findValid: 만료된 RT면 InvalidCredentialsException")
    void test_findValid_만료_예외() {
        RefreshToken expired = RefreshToken.builder()
                .user(user)
                .tokenHash("h")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> refreshTokenService.findValid("some-raw"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("findValid: 유효한 RT면 엔티티를 반환한다")
    void test_findValid_성공() {
        RefreshToken valid = RefreshToken.builder()
                .user(user)
                .tokenHash("h")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(valid));

        assertThat(refreshTokenService.findValid("some-raw")).isSameAs(valid);
    }

    @Test
    @DisplayName("rotate: 기존 RT를 삭제하고 새 RT를 발급한다")
    void test_rotate_삭제후_재발급() {
        RefreshToken current = RefreshToken.builder()
                .user(user)
                .tokenHash("old-hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();

        String newRaw = refreshTokenService.rotate(current);

        assertThat(newRaw).isNotBlank();
        verify(refreshTokenRepository).delete(current);   // 옛 토큰 폐기
        verify(refreshTokenRepository).save(any(RefreshToken.class)); // 새 토큰 발급
    }

    @Test
    @DisplayName("deleteByRawToken: 존재하는 RT만 삭제, 빈 값은 무시")
    void test_deleteByRawToken() {
        // 빈 값 → 조회조차 안 함
        refreshTokenService.deleteByRawToken(null);
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("h")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        given(refreshTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));

        refreshTokenService.deleteByRawToken("some-raw");
        verify(refreshTokenRepository).delete(token);
    }

    @Test
    @DisplayName("deleteExpired: 리포지토리 삭제 건수를 그대로 반환한다")
    void test_deleteExpired() {
        given(refreshTokenRepository.deleteByExpiresAtBefore(any())).willReturn(3);
        assertThat(refreshTokenService.deleteExpired()).isEqualTo(3);
    }
}
