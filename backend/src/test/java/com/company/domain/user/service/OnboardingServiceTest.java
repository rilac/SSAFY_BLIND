package com.company.domain.user.service;

import com.company.domain.user.controller.dto.OnboardingRequest;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidStateException;
import com.company.global.security.JwtProvider;

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

// (#6) OnboardingService 단위 테스트 + 화이트리스트 서버 검증
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private OnboardingService onboardingService;

    @Test
    @DisplayName("PENDING 유저가 유효 입력으로 온보딩하면 ACTIVE로 전환된다")
    void test_PENDING_유저_온보딩_완료시_ACTIVE_전환() {
        User pendingUser = pendingUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(pendingUser));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("new-jwt-token");

        String token = onboardingService.completeOnboarding(1L, request("긍정적인 알지", "15기", "서울"));

        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(pendingUser.getNickname()).isEqualTo("긍정적인 알지");
        assertThat(pendingUser.getCohort()).isEqualTo("15기");
        assertThat(pendingUser.getCampus()).isEqualTo("서울");
        assertThat(token).isEqualTo("new-jwt-token");
    }

    @Test
    @DisplayName("이미 ACTIVE인 유저가 (유효 입력으로) 온보딩 재시도 시 InvalidStateException이 발생한다")
    void test_이미_ACTIVE인_유저_온보딩시_InvalidStateException() {
        User activeUser = User.builder()
                .mmUserId("mm-456").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setField(activeUser, "id", 2L);
        given(userRepository.findById(2L)).willReturn(Optional.of(activeUser));

        // 화이트리스트는 통과하는 값 → user.completeOnboarding의 PENDING 가드에서 막힘
        assertThatThrownBy(() -> onboardingService.completeOnboarding(2L, request("긍정적인 알지", "15기", "대전")))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("이미 온보딩을 완료한 유저입니다.");
    }

    // --- 화이트리스트 서버 검증 (프론트 우회 차단) ---

    @Test
    @DisplayName("형식에 맞지 않는 닉네임은 거부되고 상태는 PENDING으로 유지된다")
    void test_잘못된_닉네임_거부() {
        User pendingUser = pendingUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> onboardingService.completeOnboarding(1L, request("나 개쩌는거 알지", "15기", "서울")))
                .isInstanceOf(InvalidStateException.class);
        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("화이트리스트에 없는 기수는 거부된다")
    void test_잘못된_기수_거부() {
        User pendingUser = pendingUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> onboardingService.completeOnboarding(1L, request("긍정적인 알지", "99기", "서울")))
                .isInstanceOf(InvalidStateException.class);
        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test
    @DisplayName("모집중(16기) 기수는 서버에서도 거부된다")
    void test_모집중_기수_거부() {
        User pendingUser = pendingUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> onboardingService.completeOnboarding(1L, request("긍정적인 알지", "16기", "서울")))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    @DisplayName("화이트리스트에 없는 캠퍼스는 거부된다")
    void test_잘못된_캠퍼스_거부() {
        User pendingUser = pendingUser(1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(pendingUser));

        assertThatThrownBy(() -> onboardingService.completeOnboarding(1L, request("긍정적인 알지", "15기", "뉴욕 캠퍼스")))
                .isInstanceOf(InvalidStateException.class);
        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.PENDING);
    }

    private User pendingUser(Long id) {
        User u = User.builder().mmUserId("mm-" + id).status(UserStatus.PENDING).role(UserRole.USER).build();
        setField(u, "id", id);
        return u;
    }

    // OnboardingRequest는 세터가 없어 리플렉션으로 필드 주입
    private OnboardingRequest request(String nickname, String cohort, String campus) {
        OnboardingRequest r = new OnboardingRequest();
        setField(r, "nickname", nickname);
        setField(r, "cohort", cohort);
        setField(r, "campus", campus);
        return r;
    }

    private void setField(Object obj, String fieldName, Object value) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
