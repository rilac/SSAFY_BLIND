package com.company.domain.admin.service;

import com.company.domain.admin.controller.dto.AdminReportedPostResponse;
import com.company.domain.admin.controller.dto.ReportStatsResponse;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.Report;
import com.company.domain.post.entity.ReportReason;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.post.repository.ReportRepository;
import com.company.domain.post.service.ReportService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
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
            // [FEATURE:report-detail] 기타(ETC) 신고의 상세 사유만 모아 관리자에게 전달(빈 값 제외).
            List<String> etcDetails = reports.stream()
                    .filter(r -> r.getReason() == ReportReason.ETC && r.getDetail() != null && !r.getDetail().isBlank())
                    .map(Report::getDetail)
                    .collect(Collectors.toList());
            result.add(AdminReportedPostResponse.of(post, reports.size(), reasonCounts, etcDetails));
        }
        // 정렬: 신고 건수 많은 순, 동률은 최신순(작성일 내림차순)
        result.sort(Comparator.comparingLong(AdminReportedPostResponse::getReportCount).reversed()
                .thenComparing(AdminReportedPostResponse::getCreatedAt, Comparator.reverseOrder()));
        return result;
    }

    // 숨김 해제 (도메인 메서드)
    @Transactional
    public void restorePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        post.restore();
    }

    // [FEATURE:admin-moderation] 관리자 선제적 숨김 — 신고 임계값(ReportService) 자동 숨김과 달리, 임의 글을 즉시 숨긴다.
    // 복원은 기존 restorePost(hidden=false, reviewed=true) 재사용.
    @Transactional
    public void hidePost(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        post.hide();
    }
    // [/FEATURE:admin-moderation]

    // [FEATURE:pinned-posts] 공지 고정 토글 — 관리자만(컨트롤러 /api/admin/** = ROLE_ADMIN). 토글 후 새 고정 상태 반환.
    @Transactional
    public boolean togglePin(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        post.togglePin();
        return post.isPinned();
    }
    // [/FEATURE:pinned-posts]

    // [FEATURE:report-dashboard] 일별 추이 윈도(최근 N일). 0건 날짜도 0으로 채워 차트 연속성 보장.
    private static final int DAILY_WINDOW_DAYS = 14;

    // [FEATURE:report-dashboard] 신고 통계 — 요약(총신고/신고글/숨김/미처리/처리율) + 사유별 + 일별 추이.
    @Transactional(readOnly = true)
    public ReportStatsResponse getReportStats() {
        // 사유별 집계 — enum 전체를 선언 순서로 0 채움. reason=null(레거시)은 막대에서 제외하되 total엔 포함.
        Map<ReportReason, Long> reasonCounts = new EnumMap<>(ReportReason.class);
        long totalReports = 0;
        for (Object[] row : reportRepository.countByReason()) {
            ReportReason reason = (ReportReason) row[0];
            long count = (Long) row[1];
            totalReports += count;
            if (reason != null) {
                reasonCounts.merge(reason, count, Long::sum);
            }
        }
        List<ReportStatsResponse.ReasonCount> byReason = Arrays.stream(ReportReason.values())
                .map(r -> new ReportStatsResponse.ReasonCount(r, r.getLabel(), reasonCounts.getOrDefault(r, 0L)))
                .collect(Collectors.toList());

        // 신고된 글 상태 — 검수 목록 산출 로직 재사용(글별 숨김/검토 여부 포함)
        List<AdminReportedPostResponse> reported = getReportedPosts();
        long reportedPosts = reported.size();
        long hiddenPosts = reported.stream().filter(AdminReportedPostResponse::isHidden).count();
        long pendingPosts = reported.stream()
                .filter(p -> !p.isHidden() && !p.isReviewed())
                .count();
        double resolvedRate = reportedPosts == 0 ? 0.0
                : (double) (reportedPosts - pendingPosts) / reportedPosts;

        // 일별 추이 — 최근 N일 윈도를 0으로 초기화한 뒤 since 이후 신고를 날짜별로 누적.
        LocalDate startDate = LocalDate.now().minusDays(DAILY_WINDOW_DAYS - 1L);
        Map<LocalDate, Long> dayCounts = new TreeMap<>();
        for (int i = 0; i < DAILY_WINDOW_DAYS; i++) {
            dayCounts.put(startDate.plusDays(i), 0L);
        }
        for (LocalDateTime ts : reportRepository.findCreatedAtSince(startDate.atStartOfDay())) {
            dayCounts.computeIfPresent(ts.toLocalDate(), (d, c) -> c + 1);
        }
        List<ReportStatsResponse.DailyCount> daily = dayCounts.entrySet().stream()
                .map(e -> new ReportStatsResponse.DailyCount(e.getKey(), e.getValue()))
                .collect(Collectors.toList());

        return new ReportStatsResponse(totalReports, reportedPosts, hiddenPosts, pendingPosts,
                resolvedRate, byReason, daily);
    }
    // [/FEATURE:report-dashboard]
}
