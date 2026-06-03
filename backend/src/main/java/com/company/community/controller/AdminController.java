package com.company.community.controller;

import com.company.community.dto.AdminReportedPostResponse;
import com.company.community.dto.FeedbackResponse;
import com.company.community.dto.FeedbackStatusUpdateRequest;
import com.company.community.dto.ReportStatsResponse;
import com.company.community.service.AdminService;
import com.company.community.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// 관리자 전용 — SecurityConfig에서 /api/admin/** 는 ROLE_ADMIN 필요
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final FeedbackService feedbackService;

    /**
     * GET /api/admin/posts/reported — 신고된(숨김 포함) 게시글 검수 목록
     */
    @GetMapping("/posts/reported")
    public ResponseEntity<List<AdminReportedPostResponse>> reportedPosts() {
        return ResponseEntity.ok(adminService.getReportedPosts());
    }

    /**
     * POST /api/admin/posts/{id}/restore — 숨김 해제
     */
    @PostMapping("/posts/{id}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long id) {
        adminService.restorePost(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * [FEATURE:pinned-posts] POST /api/admin/posts/{id}/pin — 공지 고정 토글. 응답 { "pinned": bool }(새 상태).
     */
    @PostMapping("/posts/{id}/pin")
    public ResponseEntity<Map<String, Boolean>> togglePin(@PathVariable Long id) {
        boolean pinned = adminService.togglePin(id);
        return ResponseEntity.ok(Map.of("pinned", pinned));
    }
    // [/FEATURE:pinned-posts]

    /**
     * [FEATURE:report-dashboard] GET /api/admin/reports/stats — 신고 통계(요약 + 사유별 + 일별 추이).
     */
    @GetMapping("/reports/stats")
    public ResponseEntity<ReportStatsResponse> reportStats() {
        return ResponseEntity.ok(adminService.getReportStats());
    }
    // [/FEATURE:report-dashboard]

    /**
     * GET /api/admin/feedback?processed={bool} — 건의함 조회.
     * processed=false(기본): 미처리만, true: 처리됨(처리 완료/수용 안 함).
     */
    @GetMapping("/feedback")
    public ResponseEntity<List<FeedbackResponse>> feedback(
            @RequestParam(defaultValue = "false") boolean processed) {
        return ResponseEntity.ok(feedbackService.getByProcessed(processed));
    }

    /**
     * PATCH /api/admin/feedback/{id}/status — 건의 처리 상태 변경(처리 완료/수용 안 함).
     */
    @PatchMapping("/feedback/{id}/status")
    public ResponseEntity<FeedbackResponse> updateFeedbackStatus(
            @PathVariable Long id,
            @Valid @RequestBody FeedbackStatusUpdateRequest request) {
        return ResponseEntity.ok(feedbackService.updateStatus(id, request.getStatus()));
    }
}
