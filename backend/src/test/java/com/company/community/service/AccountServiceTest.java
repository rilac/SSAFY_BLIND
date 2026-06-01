package com.company.community.service;

import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService; // 🗓️ 2026-06-02: 상태 변경 시 RT 폐기 검증

    @InjectMocks private AccountService accountService;

    private User activeUser() {
        User u = User.builder()
                .mmUserId("mm-1")
                .mmUsername("user1")
                .email("user1@ssafy.com")
                .nickname("열정적인스타티")
                .cohort("13기")
                .campus("서울")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        setId(u, 1L);
        return u;
    }

    @Test
    @DisplayName("휴면 전환 시 status가 DORMANT가 된다")
    void test_휴면_전환() {
        User user = activeUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        accountService.goDormant(1L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DORMANT);
        verify(refreshTokenService).deleteAllForUser(1L); // 🗓️ 휴면 시 RT 전체 폐기
    }

    @Test
    @DisplayName("회원 탈퇴 시 PII가 익명화되고 status가 WITHDRAWN이 된다")
    void test_회원_탈퇴() {
        User user = activeUser();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));

        accountService.withdraw(1L);

        assertThat(user.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(user.getNickname()).isNull();
        assertThat(user.getEmail()).isNull();
        assertThat(user.getCohort()).isNull();
        assertThat(user.getCampus()).isNull();
        // 동일 MM 계정으로 재로그인 시 매칭되지 않도록 mmUserId가 치환됨
        assertThat(user.getMmUserId()).startsWith("withdrawn-");
        verify(refreshTokenService).deleteAllForUser(1L); // 🗓️ 탈퇴 시 RT 전체 폐기
    }

    private void setId(Object obj, Long id) {
        try {
            var field = obj.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
