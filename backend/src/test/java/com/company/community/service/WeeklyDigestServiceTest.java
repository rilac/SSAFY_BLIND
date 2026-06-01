package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// [FEATURE:weekly-digest] 주간 다이제스트 서비스 단위 테스트 — 점수순 Top 글 메시지 구성 + 유저별 알림 발송/스킵 분기.
@ExtendWith(MockitoExtension.class)
class WeeklyDigestServiceTest {

    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private WeeklyDigestService weeklyDigestService;

    @BeforeEach
    void setUp() {
        // @Value 필드는 @InjectMocks로 주입되지 않으므로 직접 세팅
        ReflectionTestUtils.setField(weeklyDigestService, "topN", 5);
    }

    @Test
    @DisplayName("인기글이 있으면 전체 ACTIVE 유저에게 1위 글 링크로 다이제스트를 발송한다")
    void test_인기글_있으면_전체_발송() {
        Post top = post(100L, "스프링 부트 트랜잭션 정리");
        Post second = post(200L, "리액트 상태관리 비교");
        given(postRepository.findTopByScoreSince(any(), any(Pageable.class)))
                .willReturn(List.of(top, second));
        List<User> recipients = List.of(user(1L), user(2L), user(3L));
        given(userRepository.findByStatus(UserStatus.ACTIVE)).willReturn(recipients);

        int sent = weeklyDigestService.sendWeeklyDigest();

        assertThat(sent).isEqualTo(3);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        // 링크는 1위 글(100L)로, 메시지는 상위 제목 포함
        verify(notificationService).notifyDigest(eq(recipients), message.capture(), eq(100L));
        assertThat(message.getValue())
                .contains("스프링 부트 트랜잭션 정리")
                .contains("리액트 상태관리 비교");
    }

    @Test
    @DisplayName("최근 7일 인기글이 없으면 발송을 건너뛴다(유저 조회·알림 없음)")
    void test_인기글_없으면_스킵() {
        given(postRepository.findTopByScoreSince(any(), any(Pageable.class)))
                .willReturn(List.of());

        int sent = weeklyDigestService.sendWeeklyDigest();

        assertThat(sent).isZero();
        verify(userRepository, never()).findByStatus(any());
        verify(notificationService, never()).notifyDigest(any(), any(), any());
    }

    @Test
    @DisplayName("ACTIVE 유저가 없으면 발송하지 않는다")
    void test_수신자_없으면_스킵() {
        given(postRepository.findTopByScoreSince(any(), any(Pageable.class)))
                .willReturn(List.of(post(100L, "제목")));
        given(userRepository.findByStatus(UserStatus.ACTIVE)).willReturn(List.of());

        int sent = weeklyDigestService.sendWeeklyDigest();

        assertThat(sent).isZero();
        verify(notificationService, never()).notifyDigest(any(), any(), any());
    }

    @Test
    @DisplayName("긴 제목은 미리보기로 잘리고 메시지는 varchar(255) 한도를 넘지 않는다")
    void test_긴_제목_truncate() {
        String longTitle = "가".repeat(80);
        given(postRepository.findTopByScoreSince(any(), any(Pageable.class)))
                .willReturn(List.of(post(1L, longTitle), post(2L, longTitle), post(3L, longTitle)));
        given(userRepository.findByStatus(UserStatus.ACTIVE)).willReturn(List.of(user(1L)));

        weeklyDigestService.sendWeeklyDigest();

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationService).notifyDigest(any(), message.capture(), any());
        assertThat(message.getValue().length()).isLessThanOrEqualTo(255);
        assertThat(message.getValue()).contains("..."); // 제목 미리보기 컷
    }

    private Post post(Long id, String title) {
        Post p = Post.builder().title(title).content("내용").build();
        setId(p, id);
        return p;
    }

    private User user(Long id) {
        User u = User.builder().mmUserId("mm-" + id).status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(u, id);
        return u;
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
// [/FEATURE:weekly-digest]
