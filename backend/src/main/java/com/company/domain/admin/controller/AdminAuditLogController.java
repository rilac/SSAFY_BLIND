package com.company.domain.admin.controller;

import com.company.domain.admin.controller.dto.AdminAuditLogResponse;
import com.company.domain.admin.entity.AdminAuditAction;
import com.company.domain.admin.service.AdminAuditQueryService;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 관리자 감사 로그 조회 — /api/admin/** 이므로 ROLE_ADMIN + step-up 통과 필요.
 *
 * <p>삭제/수정 엔드포인트를 의도적으로 제공하지 않는다(append-only).
 * 감사 로그 조회 자체는 감사 대상이 아니다 — 대상으로 삼으면 조회할 때마다 로그가 늘어나는 되먹임이 생긴다.
 */
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AdminAuditLogController {

    // AdminUserController와 동일한 상한 정책(무제한 size로 전체 적재 방지).
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditQueryService adminAuditQueryService;

    @GetMapping
    public ResponseEntity<Page<AdminAuditLogResponse>> list(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) AdminAuditAction action,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize); // 정렬은 JPQL의 ORDER BY가 담당
        return ResponseEntity.ok(adminAuditQueryService.list(actorId, action, pageable));
    }
}
