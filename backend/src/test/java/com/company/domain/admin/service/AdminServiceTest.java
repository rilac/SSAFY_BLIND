package com.company.domain.admin.service;

import com.company.domain.admin.controller.dto.AdminReportedPostResponse;
import com.company.domain.admin.controller.dto.ReportStatsResponse;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.Report;
import com.company.domain.post.entity.ReportReason;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.post.repository.ReportRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private PostRepository postRepository;

    @Mock private AdminAuditService adminAuditService;

    @InjectMocks private AdminService adminService;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder().mmUserId("mm").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(user, 1L);
        post = Post.builder().title("신고된 글").content("내용").author(user).build();
        setId(post, 10L);
    }

    @Test
    @DisplayName("신고 목록은 게시글별 신고 수와 사유 집계를 반환한다")
    void test_신고목록_집계() {
        Report r1 = Report.builder().post(post).reporter(user).reason(ReportReason.SPAM).build();
        Report r2 = Report.builder().post(post).reporter(user).reason(ReportReason.SPAM).build();
        Report r3 = Report.builder().post(post).reporter(user).reason(ReportReason.OFF_TOPIC).build();
        given(reportRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(r1, r2, r3));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        List<AdminReportedPostResponse> result = adminService.getReportedPosts();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReportCount()).isEqualTo(3);
        assertThat(result.get(0).getReasonCounts())
                .containsEntry(ReportReason.SPAM, 2L)
                .containsEntry(ReportReason.OFF_TOPIC, 1L);
    }

    @Test
    @DisplayName("숨김 복원 시 hidden=false + reviewed=true(재자동숨김 제외)가 된다")
    void test_숨김_복원() {
        post.hide();
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        adminService.restorePost(99L, 10L);

        assertThat(post.isHidden()).isFalse();
        assertThat(post.isReviewed()).isTrue();
    }

    // [FEATURE:admin-moderation] 관리자 선제적 숨김 — 성공 + 없는 글 예외.
    @Test
    @DisplayName("관리자 선제적 숨김은 hidden=true로 만든다")
    void test_관리자_숨김() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        adminService.hidePost(99L, 10L);

        assertThat(post.isHidden()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 글 숨김은 NoSuchElementException")
    void test_관리자_숨김_없는글() {
        given(postRepository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.hidePost(99L, 99L))
                .isInstanceOf(NoSuchElementException.class);
    }
    // [/FEATURE:admin-moderation]

    // [FEATURE:report-dashboard] 신고 통계 — 사유별 집계 + 신고 글 상태 + 일별 추이.
    @Test
    @DisplayName("신고 통계는 사유별 집계·신고 글 상태·일별 추이를 반환한다")
    void test_신고_통계() {
        post.hide(); // 신고된 글이 숨김 처리된 상태
        given(reportRepository.countByReason()).willReturn(List.of(
                new Object[]{ReportReason.SPAM, 2L},
                new Object[]{ReportReason.OFF_TOPIC, 1L}));
        Report r1 = Report.builder().post(post).reporter(user).reason(ReportReason.SPAM).build();
        Report r2 = Report.builder().post(post).reporter(user).reason(ReportReason.SPAM).build();
        Report r3 = Report.builder().post(post).reporter(user).reason(ReportReason.OFF_TOPIC).build();
        given(reportRepository.findAllByOrderByCreatedAtDesc()).willReturn(List.of(r1, r2, r3));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(reportRepository.findCreatedAtSince(any()))
                .willReturn(List.of(LocalDateTime.now(), LocalDateTime.now()));

        ReportStatsResponse stats = adminService.getReportStats();

        // 요약
        assertThat(stats.getTotalReports()).isEqualTo(3);
        assertThat(stats.getReportedPosts()).isEqualTo(1);
        assertThat(stats.getHiddenPosts()).isEqualTo(1);
        assertThat(stats.getPendingPosts()).isZero();
        assertThat(stats.getResolvedRate()).isEqualTo(1.0);
        // 사유별 — enum 5종 전부(0 포함)
        assertThat(stats.getByReason()).hasSize(5);
        Map<ReportReason, Long> rc = stats.getByReason().stream()
                .collect(Collectors.toMap(ReportStatsResponse.ReasonCount::getReason,
                        ReportStatsResponse.ReasonCount::getCount));
        assertThat(rc).containsEntry(ReportReason.SPAM, 2L)
                .containsEntry(ReportReason.OFF_TOPIC, 1L)
                .containsEntry(ReportReason.ETC, 0L);
        // 일별 추이 — 14일 윈도(0 채움), 오늘 2건이 마지막 칸에 집계
        assertThat(stats.getDaily()).hasSize(14);
        assertThat(stats.getDaily().stream()
                .mapToLong(ReportStatsResponse.DailyCount::getCount).sum()).isEqualTo(2);
        assertThat(stats.getDaily().get(13).getCount()).isEqualTo(2);
    }
    // [/FEATURE:report-dashboard]

    // [FEATURE:pinned-posts] 공지 고정 토글 — 미고정→고정→해제, 새 상태 반환.
    @Test
    @DisplayName("공지 고정 토글은 상태를 뒤집고 새 상태를 반환한다")
    void test_공지_고정_토글() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        boolean afterPin = adminService.togglePin(99L, 10L); // 미고정 → 고정
        assertThat(afterPin).isTrue();
        assertThat(post.isPinned()).isTrue();

        boolean afterUnpin = adminService.togglePin(99L, 10L); // 고정 → 해제
        assertThat(afterUnpin).isFalse();
        assertThat(post.isPinned()).isFalse();
    }
    // [/FEATURE:pinned-posts]

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
