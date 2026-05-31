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

    private LocalDateTime createdAt;

    public static AdminReportedPostResponse of(Post post, long reportCount,
                                               Map<ReportReason, Long> reasonCounts) {
        return new AdminReportedPostResponse(
                post.getId(),
                post.getTitle(),
                reportCount,
                reasonCounts,
                post.isHidden(),
                post.getCreatedAt()
        );
    }
}
