package com.company.domain.post.service;

import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.Report;
import com.company.domain.post.entity.ReportReason;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.post.repository.ReportRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
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
        given(userRepository.existsById(1L)).willReturn(true);

        reportService.report(1L, 10L, ReportReason.SPAM, null, UserRole.USER);

        verify(reportRepository).insertIgnore(eq(10L), eq(1L), eq("SPAM"), isNull(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("이미 신고한 게시글은 멱등 처리되어 저장되지 않는다")
    void test_중복_신고_멱등() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(true);

        reportService.report(1L, 10L, ReportReason.SPAM, null, UserRole.USER);

        verify(reportRepository, never()).insertIgnore(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("신고 누적 5건 도달 시 게시글이 자동 숨김 처리된다")
    void test_신고_5건_자동숨김() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.existsById(1L)).willReturn(true);
        given(reportRepository.insertIgnore(any(), any(), any(), any(), any())).willReturn(1);
        given(reportRepository.countByPostId(10L)).willReturn(5L);

        reportService.report(1L, 10L, ReportReason.OFF_TOPIC, null, UserRole.USER);

        assertThat(post.isHidden()).isTrue();
    }

    @Test
    @DisplayName("신고 누적이 임계값 미만이면 숨김되지 않는다")
    void test_신고_임계값_미만() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.existsById(1L)).willReturn(true);
        given(reportRepository.insertIgnore(any(), any(), any(), any(), any())).willReturn(1);
        given(reportRepository.countByPostId(10L)).willReturn(3L);

        reportService.report(1L, 10L, ReportReason.ETC, null, UserRole.USER);

        assertThat(post.isHidden()).isFalse();
    }

    // [FEATURE:report-detail] 기타 신고의 상세 사유가 trim되어 저장된다.
    @Test
    @DisplayName("기타(ETC) 신고 시 상세 사유가 trim되어 저장된다")
    void test_ETC_상세사유_저장() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.existsById(1L)).willReturn(true);
        given(reportRepository.insertIgnore(any(), any(), any(), any(), any())).willReturn(1);

        reportService.report(1L, 10L, ReportReason.ETC, "  광고 도배예요  ", UserRole.USER);

        // 상세 사유는 이제 엔티티가 아니라 네이티브 INSERT 파라미터로 전달된다.
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(reportRepository).insertIgnore(eq(10L), eq(1L), eq("ETC"), captor.capture(), any(LocalDateTime.class));
        assertThat(captor.getValue()).isEqualTo("광고 도배예요");
    }

    @Test
    @DisplayName("관리자가 복원(검수 완료)한 글은 신고가 임계값을 넘어도 재자동숨김되지 않는다 (§1-1)")
    void test_복원글_재숨김_방지() {
        post.restore(); // 복원 → reviewed=true
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.existsByPostIdAndReporterId(10L, 1L)).willReturn(false);
        given(userRepository.existsById(1L)).willReturn(true);
        given(reportRepository.insertIgnore(any(), any(), any(), any(), any())).willReturn(1);

        reportService.report(1L, 10L, ReportReason.OFF_TOPIC, null, UserRole.USER);

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
