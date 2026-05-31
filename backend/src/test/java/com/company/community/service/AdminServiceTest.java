package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.Report;
import com.company.community.domain.ReportReason;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.dto.AdminReportedPostResponse;
import com.company.community.repository.PostRepository;
import com.company.community.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private PostRepository postRepository;

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

        adminService.restorePost(10L);

        assertThat(post.isHidden()).isFalse();
        assertThat(post.isReviewed()).isTrue();
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
