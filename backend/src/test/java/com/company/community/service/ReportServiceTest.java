package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.Report;
import com.company.community.domain.ReportReason;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.repository.PostRepository;
import com.company.community.repository.ReportRepository;
import com.company.community.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ReportService reportService;

    private User reporter;
    private Post post;

    @BeforeEach
    void setUp() {
        reporter = User.builder().mmUserId("mm").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(reporter, 1L);
        post = Post.builder().title("제목").content("내용").author(reporter).build();
        setId(post, 10L);
    }

    @Test
    @DisplayName("처음 신고하면 저장된다")
    void test_신고_저장() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));

        reportService.report(1L, 10L, ReportReason.SPAM);

        verify(reportRepository).save(any(Report.class));
    }

    @Test
    @DisplayName("이미 신고한 게시글은 멱등 처리되어 저장되지 않는다")
    void test_중복_신고_멱등() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(true);

        reportService.report(1L, 10L, ReportReason.SPAM);

        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("신고 누적 5건 도달 시 게시글이 자동 숨김 처리된다")
    void test_신고_5건_자동숨김() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(reportRepository.countByPostId(10L)).willReturn(5L);

        reportService.report(1L, 10L, ReportReason.OFF_TOPIC);

        assertThat(post.isHidden()).isTrue();
    }

    @Test
    @DisplayName("신고 누적이 임계값 미만이면 숨김되지 않는다")
    void test_신고_임계값_미만() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));
        given(reportRepository.countByPostId(10L)).willReturn(3L);

        reportService.report(1L, 10L, ReportReason.ETC);

        assertThat(post.isHidden()).isFalse();
    }

    @Test
    @DisplayName("관리자가 복원(검수 완료)한 글은 신고가 임계값을 넘어도 재자동숨김되지 않는다 (§1-1)")
    void test_복원글_재숨김_방지() {
        post.restore(); // 복원 → reviewed=true
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(reporter));

        reportService.report(1L, 10L, ReportReason.OFF_TOPIC);

        // reviewed 글은 임계값 카운트 쿼리 자체를 건너뛰고 숨김되지 않는다
        assertThat(post.isHidden()).isFalse();
        verify(reportRepository, never()).countByPostId(any());
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
