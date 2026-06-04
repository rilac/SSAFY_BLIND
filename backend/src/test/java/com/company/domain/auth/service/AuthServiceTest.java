package com.company.domain.auth.service;

import com.company.domain.auth.controller.dto.LoginResponse;
import com.company.domain.auth.controller.dto.TokenPair;
import com.company.domain.auth.entity.RefreshToken;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidCredentialsException;
import com.company.global.mattermost.MattermostClient;
import com.company.global.mattermost.MattermostUser;
import com.company.global.security.JwtProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// (#6) AuthService 단위 테스트 — Mockito 기반
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private MattermostClient mmClient;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("신규 유저 로그인 시 PENDING 상태로 저장된다")
    void test_신규유저_로그인시_PENDING_상태로_저장된다() {
        // Arrange
        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-user-123");
        given(mockMmUser.getUsername()).willReturn("testuser");
        given(mockMmUser.getEmail()).willReturn("test@example.com");

        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-user-123")).willReturn(false);

        User savedUser = User.builder()
                .mmUserId("mm-user-123")
                .mmUsername("testuser")
                .email("test@example.com")
                .status(UserStatus.PENDING)
                .role(UserRole.USER)
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);
        given(userRepository.findByMmUserId("mm-user-123")).willReturn(Optional.of(savedUser));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt-token");
        given(refreshTokenService.issue(any())).willReturn("mock-refresh-token");

        // Act
        LoginResponse result = authService.login("testuser", "password");

        // Assert — save() 호출 검증 및 저장된 User의 status가 PENDING인지 확인
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(result.isNewUser()).isTrue();
    }

    @Test
    @DisplayName("기존 유저 재로그인 시 isNewUser가 false이다")
    void test_기존유저_재로그인시_isNewUser가_false이다() {
        // Arrange — 기존 유저이므로 getUsername(), getEmail()은 호출되지 않음
        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-user-456");

        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        // 기존 유저 — existsByMmUserId=true → save() 블록을 건너뜀
        given(userRepository.existsByMmUserId("mm-user-456")).willReturn(true);

        User existingUser = User.builder()
                .mmUserId("mm-user-456")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-user-456")).willReturn(Optional.of(existingUser));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt-token");
        given(refreshTokenService.issue(any())).willReturn("mock-refresh-token");

        // Act
        LoginResponse result = authService.login("existuser", "password");

        // Assert — save()가 호출되지 않아야 하고 isNewUser=false
        verify(userRepository, never()).save(any());
        assertThat(result.isNewUser()).isFalse();
    }

    @Test
    @DisplayName("M-NEW-6: 지정된 MM 계정은 로그인 시 ADMIN으로 승격된다")
    void test_지정계정_로그인시_ADMIN으로_승격된다() {
        // Arrange — app.admin.bootstrap-usernames에 "adminuser" 지정
        ReflectionTestUtils.setField(authService, "adminBootstrapUsernames", "adminuser, otheruser");

        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-admin-1");
        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-admin-1")).willReturn(true);

        User existing = User.builder()
                .mmUserId("mm-admin-1")
                .mmUsername("adminuser")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-admin-1")).willReturn(Optional.of(existing));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt-token");
        given(refreshTokenService.issue(any())).willReturn("mock-refresh-token");

        // Act
        authService.login("adminuser", "password");

        // Assert — role이 ADMIN으로 승격됨
        assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("M-NEW-6: 미지정 계정은 ADMIN으로 승격되지 않는다")
    void test_미지정계정은_승격되지_않는다() {
        ReflectionTestUtils.setField(authService, "adminBootstrapUsernames", "adminuser");

        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-normal-1");
        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-normal-1")).willReturn(true);

        User existing = User.builder()
                .mmUserId("mm-normal-1")
                .mmUsername("normaluser")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-normal-1")).willReturn(Optional.of(existing));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt-token");
        given(refreshTokenService.issue(any())).willReturn("mock-refresh-token");

        authService.login("normaluser", "password");

        assertThat(existing.getRole()).isEqualTo(UserRole.USER);
    }

    // 🗓️ 2026-06-02: Access/Refresh 토큰 분리 — refresh 재발급

    @Test
    @DisplayName("refresh: 유효한 RT면 새 AT를 발급하고 RT를 회전한다")
    void test_refresh_성공() {
        User user = User.builder()
                .mmUserId("mm-1")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("h")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        given(refreshTokenService.findValid("raw-rt")).willReturn(token);
        given(refreshTokenService.rotate(token)).willReturn("new-rt");
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("new-at");

        TokenPair pair = authService.refresh("raw-rt");

        assertThat(pair.accessToken()).isEqualTo("new-at");
        assertThat(pair.refreshToken()).isEqualTo("new-rt");
    }

    @Test
    @DisplayName("refresh: 휴면/탈퇴 계정은 재발급을 거부하고 회전하지 않는다")
    void test_refresh_비활성계정_거부() {
        User dormant = User.builder()
                .mmUserId("mm-2")
                .status(UserStatus.DORMANT)
                .role(UserRole.USER)
                .build();
        RefreshToken token = RefreshToken.builder()
                .user(dormant)
                .tokenHash("h")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        given(refreshTokenService.findValid("raw-rt")).willReturn(token);

        assertThatThrownBy(() -> authService.refresh("raw-rt"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).rotate(any());
    }

    @Test
    @DisplayName("refresh: 무효/만료 RT면 예외를 그대로 전파한다")
    void test_refresh_무효토큰_전파() {
        given(refreshTokenService.findValid("bad-rt"))
                .willThrow(new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다."));

        assertThatThrownBy(() -> authService.refresh("bad-rt"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).rotate(any());
    }
}
