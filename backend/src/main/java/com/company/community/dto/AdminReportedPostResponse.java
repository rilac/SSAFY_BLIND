package com.company.community.dto;

import com.company.community.domain.Post;
import com.company.community.domain.ReportReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

// 관리자 신고 검수 목록 항목 — 신고 수 + 사유별 집계 + 숨김 여부
@Getter
@AllArgsConstructor
public class AdminReportedPostResponse {

    private Long postId;
    private String title;
    private long reportCount;

    // 사유별 신고 수 (ReportReason → count)
    private Map<ReportReason, Long> reasonCounts;

    @JsonProperty("hidden")
    private boolean hidden;

    // 관리자가 복원(검수 완료)한 글 — 재자동숨김 제외 대상(§1-1). UI에서 "검토완료" 표시.
    @JsonProperty("reviewed")
    private boolean reviewed;

    private LocalDateTime createdAt;

    public static AdminReportedPostResponse of(Post post, long reportCount,
                                               Map<ReportReason, Long> reasonCounts) {
        return new AdminReportedPostResponse(
                post.getId(),
                post.getTitle(),
                reportCount,
                reasonCounts,
                post.isHidden(),
                post.isReviewed(),
                post.getCreatedAt()
        );
    }
}
