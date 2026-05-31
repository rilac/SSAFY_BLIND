package com.company.community.service;

import com.company.community.client.MattermostClient;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.dto.LoginResponse;
import com.company.community.dto.MattermostUser;
import com.company.community.repository.UserRepository;
import com.company.community.security.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

        // Act
        LoginResponse result = authService.login("existuser", "password");

        // Assert — save()가 호출되지 않아야 하고 isNewUser=false
        verify(userRepository, never()).save(any());
        assertThat(result.isNewUser()).isFalse();
    }
}
