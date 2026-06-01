package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.Report;
import com.company.community.domain.ReportReason;
import com.company.community.dto.AdminReportedPostResponse;
import com.company.community.repository.PostRepository;
import com.company.community.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

// 관리자 — 신고 검수 / 숨김 복원
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;

    // 신고된 게시글 목록 (신고 수 desc) — 사유별 집계 + 숨김 여부 포함
    @Transactional(readOnly = true)
    public List<AdminReportedPostResponse> getReportedPosts() {
        Map<Long, List<Report>> byPostId = reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .collect(Collectors.groupingBy(r -> r.getPost().getId()));

        List<AdminReportedPostResponse> result = new ArrayList<>();
        for (Map.Entry<Long, List<Report>> entry : byPostId.entrySet()) {
            Post post = postRepository.findById(entry.getKey()).orElse(null);
            if (post == null) continue; // 삭제된 글은 건너뜀
            List<Report> reports = entry.getValue();
            Map<ReportReason, Long> reasonCounts = reports.stream()
                    .collect(Collectors.groupingBy(Report::getReason, Collectors.counting()));
            result.add(AdminReportedPostResponse.of(post, reports.size(), reasonCounts));
        }
        result.sort(Comparator.comparingLong(AdminReportedPostResponse::getReportCount).reversed());
        return result;
    }

    // 숨김 해제 (도메인 메서드)
    @Transactional
    public void restorePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        post.restore();
    }

    // [FEATURE:pinned-posts] 공지 고정 토글 — 관리자만(컨트롤러 /api/admin/** = ROLE_ADMIN). 토글 후 새 고정 상태 반환.
    @Transactional
    public boolean togglePin(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        post.togglePin();
        return post.isPinned();
    }
    // [/FEATURE:pinned-posts]
}
