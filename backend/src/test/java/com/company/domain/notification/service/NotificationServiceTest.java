package com.company.domain.notification.service;

import com.company.domain.notification.entity.Notification;
import com.company.domain.notification.entity.NotificationType;
import com.company.domain.notification.repository.NotificationRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.global.exception.ForbiddenException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;

    @InjectMocks private NotificationService notificationService;

    private User recipient() {
        User u = User.builder().mmUserId("mm").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(u, 1L);
        return u;
    }

    @Test
    @DisplayName("댓글 알림 생성 시 COMMENT 타입과 postId가 저장된다")
    void test_댓글_알림_생성() {
        notificationService.notifyComment(recipient(), 10L, "안녕하세요 테스트 게시글 제목입니다 길어요");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT);
        assertThat(saved.getPostId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("본인 알림은 읽음 처리된다")
    void test_읽음_처리() {
        Notification n = Notification.builder()
                .recipient(recipient())
                .type(NotificationType.LIKE)
                .message("msg")
                .postId(10L)
                .build();
        given(notificationRepository.findById(100L)).willReturn(Optional.of(n));

        notificationService.markRead(1L, 100L);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    @DisplayName("타인의 알림 읽음 처리 시 ForbiddenException이 발생한다")
    void test_타인_알림_읽음_금지() {
        Notification n = Notification.builder()
                .recipient(recipient()) // recipient id = 1
                .type(NotificationType.LIKE)
                .message("msg")
                .build();
        given(notificationRepository.findById(100L)).willReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markRead(2L, 100L))
                .isInstanceOf(ForbiddenException.class);
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
