package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.Report;
import com.company.community.domain.ReportReason;
import com.company.community.domain.User;
import com.company.community.repository.PostRepository;
import com.company.community.repository.ReportRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// 게시글 신고 — 신고자별 1회(멱등). 누적 5건 도달 시 자동 숨김.
@Service
@RequiredArgsConstructor
public class ReportService {

    // 자동 숨김 임계값 (서로 다른 신고자 누적 수)
    private static final int REPORT_HIDE_THRESHOLD = 5;

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public void report(Long userId, Long postId, ReportReason reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        // 이미 신고한 경우 멱등 처리 (재신고 무시)
        if (reportRepository.existsByPostIdAndReporterId(postId, userId)) {
            return;
        }

        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
        try {
            reportRepository.save(Report.builder()
                    .post(post)
                    .reporter(reporter)
                    .reason(reason)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 중복 신고 — 멱등 무시
            return;
        }

        // 누적 신고가 임계값에 도달하면 자동 숨김 (도메인 메서드, dirty checking 반영).
        // 단, 관리자가 복원(검수 완료)한 글은 재신고가 쌓여도 재자동숨김하지 않는다(§1-1 재숨김 루프 방지).
        if (!post.isHidden() && !post.isReviewed()
                && reportRepository.countByPostId(postId) >= REPORT_HIDE_THRESHOLD) {
            post.hide();
        }
    }
}
