package com.company.community.service;

import com.company.community.domain.Feedback;
import com.company.community.domain.FeedbackStatus;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.dto.FeedbackRequest;
import com.company.community.dto.FeedbackResponse;
import com.company.community.repository.FeedbackRepository;
import com.company.community.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock private FeedbackRepository feedbackRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private FeedbackService feedbackService;

    private User user() {
        User u = User.builder()
                .mmUserId("mm")
                .nickname("긍정적인 스타티")
                .cohort("13기")
                .campus("서울")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        setId(u, 1L);
        return u;
    }

    @Test
    @DisplayName("건의 작성 시 저장된다")
    void test_건의_작성() {
        given(userRepository.findById(1L)).willReturn(Optional.of(user()));
        FeedbackRequest req = new FeedbackRequest();
        setField(req, "title", "제안");
        setField(req, "content", "다크모드 기본값을 바꿔주세요");

        feedbackService.create(1L, req);

        verify(feedbackRepository).save(any(Feedback.class));
    }

    @Test
    @DisplayName("미처리 건의 목록은 작성자 가명과 함께 반환된다")
    void test_건의_목록() {
        Feedback fb = Feedback.builder().title("제안").content("내용").author(user()).build();
        given(feedbackRepository.findByStatusOrderByCreatedAtDesc(FeedbackStatus.PENDING)).willReturn(List.of(fb));

        List<FeedbackResponse> result = feedbackService.getByProcessed(false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAuthor().getNickname()).isEqualTo("긍정적인 스타티");
        assertThat(result.get(0).getStatus()).isEqualTo(FeedbackStatus.PENDING);
    }

    @Test
    @DisplayName("건의 처리 상태를 변경하면 엔티티에 반영된다")
    void test_건의_처리() {
        Feedback fb = Feedback.builder().title("제안").content("내용").author(user()).build();
        given(feedbackRepository.findById(1L)).willReturn(Optional.of(fb));

        FeedbackResponse result = feedbackService.updateStatus(1L, FeedbackStatus.RESOLVED);

        assertThat(result.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
        assertThat(fb.getStatus()).isEqualTo(FeedbackStatus.RESOLVED);
    }

    private void setId(Object obj, Long id) {
        setField(obj, "id", id);
    }

    private void setField(Object obj, String name, Object value) {
        try {
            var field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
