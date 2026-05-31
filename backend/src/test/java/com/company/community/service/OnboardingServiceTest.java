package com.company.community.service;

import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.dto.OnboardingRequest;
import com.company.community.exception.InvalidStateException;
import com.company.community.repository.UserRepository;
import com.company.community.security.JwtProvider;
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

// (#6) OnboardingService 단위 테스트
@ExtendWith(MockitoExtension.class)
class OnboardingServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtProvider jwtProvider;

    @InjectMocks
    private OnboardingService onboardingService;

    // 리플렉션으로 private 필드 설정
    private void setField(Object obj, String fieldName, Object value) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("PENDING 유저 온보딩 완료 시 ACTIVE로 전환된다")
    void test_PENDING_유저_온보딩_완료시_ACTIVE_전환() {
        // Arrange
        User pendingUser = User.builder()
                .mmUserId("mm-123")
                .status(UserStatus.PENDING)
                .role(UserRole.USER)
                .build();
        setField(pendingUser, "id", 1L);

        OnboardingRequest request = new OnboardingRequest();
        setField(request, "nickname", "익명유저");
        setField(request, "cohort", "13기");
        setField(request, "campus", "서울");

        given(userRepository.findById(1L)).willReturn(Optional.of(pendingUser));
        given(jwtProvider.generateToken(any(), any(), any())).willReturn("new-jwt-token");

        // Act
        String token = onboardingService.completeOnboarding(1L, request);

        // Assert — ACTIVE 전환 확인
        assertThat(pendingUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(pendingUser.getNickname()).isEqualTo("익명유저");
        assertThat(pendingUser.getCohort()).isEqualTo("13기");
        assertThat(pendingUser.getCampus()).isEqualTo("서울");
        assertThat(token).isEqualTo("new-jwt-token");
    }

    @Test
    @DisplayName("이미 ACTIVE인 유저가 온보딩 시도 시 InvalidStateException이 발생한다")
    void test_이미_ACTIVE인_유저_온보딩시_InvalidStateException() {
        // Arrange
        User activeUser = User.builder()
                .mmUserId("mm-456")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        setField(activeUser, "id", 2L);

        OnboardingRequest request = new OnboardingRequest();
        setField(request, "nickname", "중복유저");
        setField(request, "cohort", "12기");
        setField(request, "campus", "대전");

        given(userRepository.findById(2L)).willReturn(Optional.of(activeUser));

        // Assert — ACTIVE 상태에서 온보딩 재시도 시 예외
        assertThatThrownBy(() -> onboardingService.completeOnboarding(2L, request))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("이미 온보딩을 완료한 유저입니다.");
    }
}
