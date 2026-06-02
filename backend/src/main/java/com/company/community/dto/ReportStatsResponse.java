package com.company.community.dto;

import com.company.community.domain.ReportReason;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

// [FEATURE:report-dashboard] 관리자 신고 대시보드 통계 — 요약 + 사유별 집계 + 일별 추이.
// 기존 reports/posts 집계만 사용(스키마 무변경).
@Getter
@AllArgsConstructor
public class ReportStatsResponse {

    private long totalReports;   // 전체 신고 건수
    private long reportedPosts;  // 신고된 글 수(distinct post)
    private long hiddenPosts;    // 그 중 숨김 처리된 글
    private long pendingPosts;   // 그 중 미처리(숨김도 검토완료도 아님)
    private double resolvedRate; // 처리율 = (reportedPosts - pendingPosts) / reportedPosts (0~1, 신고 글 없으면 0)

    private List<ReasonCount> byReason; // 사유별(enum 전체, 0 포함, 선언 순서)
    private List<DailyCount> daily;     // 최근 N일 일별(연속 날짜, 0 채움)

    @Getter
    @AllArgsConstructor
    public static class ReasonCount {
        private ReportReason reason;
        private String label;  // enum 한글 라벨
        private long count;
    }

    @Getter
    @AllArgsConstructor
    public static class DailyCount {
        private LocalDate date;
        private long count;
    }
}
