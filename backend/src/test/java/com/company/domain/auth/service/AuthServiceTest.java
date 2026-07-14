package com.company.domain.auth.service;

import com.company.domain.auth.controller.dto.LoginResponse;
import com.company.domain.auth.controller.dto.TokenPair;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidCredentialsException;
import com.company.global.mattermost.MattermostClient;
import com.company.global.mattermost.MattermostUser;
import com.company.global.security.AccessTokenStore;
import com.company.global.security.JwtProvider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

// (#6) AuthService 단위 테스트 — 팬텀 토큰(AccessTokenStore) + rememberMe 반영
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private MattermostClient mmClient;
    @Mock private UserRepository userRepository;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AccessTokenStore accessTokenStore;

    @InjectMocks private AuthService authService;

    @Test
    @DisplayName("신규 유저 로그인 시 PENDING 상태로 저장되고 팬텀을 반환한다")
    void test_신규유저_로그인시_PENDING_상태로_저장된다() {
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
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt");
        given(accessTokenStore.store(any(), any())).willReturn("phantom-token");
        given(refreshTokenService.issue(any(), anyBoolean())).willReturn("mock-refresh-token");

        LoginResponse result = authService.login("testuser", "password", true);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.PENDING);
        assertThat(result.isNewUser()).isTrue();
        // 클라이언트엔 JWT가 아니라 팬텀만 전달된다.
        assertThat(result.getAccessToken()).isEqualTo("phantom-token");
    }

    @Test
    @DisplayName("rememberMe=false면 Refresh Token을 발급하지 않는다(null)")
    void test_rememberMe_false면_RT_미발급() {
        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-user-777");
        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-user-777")).willReturn(true);

        User existing = User.builder()
                .mmUserId("mm-user-777")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-user-777")).willReturn(Optional.of(existing));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt");
        given(accessTokenStore.store(any(), any())).willReturn("phantom-token");
        given(refreshTokenService.issue(any(), eq(false))).willReturn(null);

        LoginResponse result = authService.login("existuser", "password", false);

        assertThat(result.getRefreshToken()).isNull();
        verify(refreshTokenService).issue(existing, false);
    }

    @Test
    @DisplayName("기존 유저 재로그인 시 isNewUser가 false이다")
    void test_기존유저_재로그인시_isNewUser가_false이다() {
        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-user-456");

        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-user-456")).willReturn(true);

        User existingUser = User.builder()
                .mmUserId("mm-user-456")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-user-456")).willReturn(Optional.of(existingUser));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt");
        given(accessTokenStore.store(any(), any())).willReturn("phantom-token");
        given(refreshTokenService.issue(any(), anyBoolean())).willReturn("mock-refresh-token");

        LoginResponse result = authService.login("existuser", "password", true);

        verify(userRepository, never()).save(any());
        assertThat(result.isNewUser()).isFalse();
    }

    @Test
    @DisplayName("M-NEW-6: 지정된 MM 계정은 로그인 시 ADMIN으로 승격된다")
    void test_지정계정_로그인시_ADMIN으로_승격된다() {
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
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("mock-jwt");
        given(accessTokenStore.store(any(), any())).willReturn("phantom-token");
        given(refreshTokenService.issue(any(), anyBoolean())).willReturn("mock-refresh-token");

        authService.login("adminuser", "password", true);

        assertThat(existing.getRole()).isEqualTo(UserRole.ADMIN);
    }

    @Test
    @DisplayName("졸업(비활동) 기수 계정은 로그인이 거부된다")
    void test_졸업기수_로그인_거부() {
        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-grad-1");
        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-grad-1")).willReturn(true);

        User graduated = User.builder()
                .mmUserId("mm-grad-1")
                .nickname("긍정적인 알지")
                .cohort("14기") // 화이트리스트에서 제외된 졸업 기수
                .campus("서울")
                .status(UserStatus.DORMANT)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-grad-1")).willReturn(Optional.of(graduated));

        assertThatThrownBy(() -> authService.login("graduser", "password", true))
                .isInstanceOf(com.company.global.exception.ForbiddenException.class);
        verify(accessTokenStore, never()).store(any(), any());
    }

    @Test
    @DisplayName("차단(BLOCKED) 계정은 로그인이 거부된다")
    void test_차단계정_로그인_거부() {
        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-blocked-1");
        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-blocked-1")).willReturn(true);

        User blocked = User.builder()
                .mmUserId("mm-blocked-1")
                .nickname("긍정적인 알지")
                .cohort("15기")
                .campus("서울")
                .status(UserStatus.BLOCKED)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-blocked-1")).willReturn(Optional.of(blocked));

        assertThatThrownBy(() -> authService.login("blockeduser", "password", true))
                .isInstanceOf(com.company.global.exception.ForbiddenException.class);
        verify(accessTokenStore, never()).store(any(), any());
    }

    // 팬텀 토큰 재발급 — refresh

    @Test
    @DisplayName("refresh: 유효한 RT면 새 팬텀을 발급하고 RT를 회전한다")
    void test_refresh_성공() {
        User user = User.builder()
                .id(42L)
                .mmUserId("mm-1")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        given(refreshTokenService.findValidUserId("raw-rt")).willReturn(42L);
        given(userRepository.findById(42L)).willReturn(Optional.of(user));
        given(refreshTokenService.rotate("raw-rt", user)).willReturn("new-rt");
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("new-jwt");
        given(accessTokenStore.store("new-jwt", 42L)).willReturn("new-phantom");

        TokenPair pair = authService.refresh("raw-rt");

        assertThat(pair.accessToken()).isEqualTo("new-phantom");
        assertThat(pair.refreshToken()).isEqualTo("new-rt");
    }

    @Test
    @DisplayName("refresh: 휴면/탈퇴/차단 계정은 재발급을 거부하고 회전하지 않는다")
    void test_refresh_비활성계정_거부() {
        User dormant = User.builder()
                .id(43L)
                .mmUserId("mm-2")
                .status(UserStatus.DORMANT)
                .role(UserRole.USER)
                .build();
        given(refreshTokenService.findValidUserId("raw-rt")).willReturn(43L);
        given(userRepository.findById(43L)).willReturn(Optional.of(dormant));

        assertThatThrownBy(() -> authService.refresh("raw-rt"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).rotate(any(), any());
    }

    @Test
    @DisplayName("refresh: 무효/만료 RT면 예외를 그대로 전파한다")
    void test_refresh_무효토큰_전파() {
        given(refreshTokenService.findValidUserId("bad-rt"))
                .willThrow(new InvalidCredentialsException("유효하지 않은 리프레시 토큰입니다."));

        assertThatThrownBy(() -> authService.refresh("bad-rt"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(refreshTokenService, never()).rotate(any(), any());
    }

    @Test
    @DisplayName("활동 기수(화이트리스트) 휴면 계정은 재로그인 시 ACTIVE로 복구된다")
    void test_활동기수_휴면계정_재활성화() {
        MattermostUser mockMmUser = mock(MattermostUser.class);
        given(mockMmUser.getId()).willReturn("mm-dormant-1");
        given(mmClient.login(any(), any())).willReturn(mockMmUser);
        given(userRepository.existsByMmUserId("mm-dormant-1")).willReturn(true);

        User dormant = User.builder()
                .id(7L)
                .mmUserId("mm-dormant-1")
                .nickname("긍정적인 알지")
                .cohort("15기") // 화이트리스트에 포함된 활동 기수
                .campus("서울")
                .status(UserStatus.DORMANT)
                .role(UserRole.USER)
                .build();
        given(userRepository.findByMmUserId("mm-dormant-1")).willReturn(Optional.of(dormant));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("jwt");
        given(accessTokenStore.store("jwt", 7L)).willReturn("phantom");

        authService.login("dormantuser", "password", false);

        assertThat(dormant.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(accessTokenStore).store("jwt", 7L);
    }

    // 로그아웃 — 서버측 즉시 폐기(개편의 핵심 보장)

    @Test
    @DisplayName("logout: 제시된 RT 삭제 + Access 팬텀을 Redis에서 폐기한다")
    void test_logout_서버측_폐기() {
        authService.logout("raw-rt", "phantom");

        verify(refreshTokenService).deleteByRawToken("raw-rt");
        verify(accessTokenStore).revoke("phantom");
    }

    @Test
    @DisplayName("logout: rememberMe=false 세션(RT 없음)도 팬텀은 폐기한다")
    void test_logout_RT없는_세션() {
        authService.logout(null, "phantom");

        verify(refreshTokenService).deleteByRawToken(null);
        verify(accessTokenStore).revoke("phantom");
    }
}
