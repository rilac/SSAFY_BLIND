package com.company.domain.admin.service;

import com.company.domain.auth.service.RefreshTokenService;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// R8: 관리자 유저 차단/해제 — 차단 시 즉시 강제 로그아웃(RT+AT 팬텀+step-up 마커 폐기)이 핵심 계약
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AdminStepUpService adminStepUpService;

    @Mock private AdminAuditService adminAuditService;

    @InjectMocks private AdminUserService adminUserService;

    @Test
    @DisplayName("block: 상태를 BLOCKED로 바꾸고 모든 토큰과 step-up 마커를 즉시 폐기한다")
    void test_block_강제로그아웃() {
        User user = user(UserStatus.ACTIVE, "긍정적인 알지", "15기");
        given(userRepository.findById(10L)).willReturn(Optional.of(user));

        adminUserService.block(99L, 10L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);
        verify(refreshTokenService).deleteAllForUser(10L); // 내부에서 AT 팬텀까지 폐기
        verify(adminStepUpService).clear(10L);
    }

    @Test
    @DisplayName("block: 탈퇴(WITHDRAWN) 계정은 차단할 수 없다")
    void test_block_탈퇴계정_거부() {
        User user = user(UserStatus.WITHDRAWN, null, null);
        given(userRepository.findById(11L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.block(99L, 11L))
                .isInstanceOf(InvalidStateException.class);
        verify(refreshTokenService, never()).deleteAllForUser(any());
    }

    @Test
    @DisplayName("unblock: 온보딩 완료 이력이 있으면 ACTIVE로 복원한다")
    void test_unblock_온보딩완료_ACTIVE() {
        User user = user(UserStatus.BLOCKED, "긍정적인 알지", "15기");
        given(userRepository.findById(12L)).willReturn(Optional.of(user));

        adminUserService.unblock(99L, 12L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("unblock: 온보딩 전 계정은 PENDING으로 복원한다")
    void test_unblock_온보딩전_PENDING() {
        User user = user(UserStatus.BLOCKED, null, null);
        given(userRepository.findById(13L)).willReturn(Optional.of(user));

        adminUserService.unblock(99L, 13L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("unblock: 차단 상태가 아니면 거부한다")
    void test_unblock_비차단_거부() {
        User user = user(UserStatus.ACTIVE, "긍정적인 알지", "15기");
        given(userRepository.findById(14L)).willReturn(Optional.of(user));

        assertThatThrownBy(() -> adminUserService.unblock(99L, 14L))
                .isInstanceOf(InvalidStateException.class);
    }

    private User user(UserStatus status, String nickname, String cohort) {
        return User.builder()
                .id(10L)
                .mmUserId("mm-1")
                .nickname(nickname)
                .cohort(cohort)
                .campus(cohort == null ? null : "서울")
                .status(status)
                .role(UserRole.USER)
                .build();
    }
}
