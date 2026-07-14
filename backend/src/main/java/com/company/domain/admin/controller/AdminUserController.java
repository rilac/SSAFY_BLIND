package com.company.domain.admin.controller;

import com.company.domain.admin.controller.dto.AdminUserResponse;
import com.company.domain.admin.service.AdminUserService;
import com.company.domain.user.entity.UserStatus;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// R8: 관리자 유저 관리 — /api/admin/** 은 ROLE_ADMIN + step-up(2차 인증) 통과 시에만 접근 가능(AdminStepUpInterceptor).
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    // 페이지 크기 상한 — 무제한 size로 전체 테이블을 한 응답에 적재하는 것을 방지.
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;

    /** GET /api/admin/users — 검색(닉네임/MM계정/이메일 keyword + 기수/지역/상태 필터) + 페이징. */
    @GetMapping
    public ResponseEntity<Page<AdminUserResponse>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cohort,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // 음수 page/size는 PageRequest가 500으로 터지므로 하한·상한을 함께 보정.
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(adminUserService.search(keyword, cohort, campus, status, pageable));
    }

    /** GET /api/admin/users/{id} — 유저 상세. */
    @GetMapping("/{id}")
    public ResponseEntity<AdminUserResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(adminUserService.get(id));
    }

    /** POST /api/admin/users/{id}/block — 차단(즉시 강제 로그아웃). */
    @PostMapping("/{id}/block")
    public ResponseEntity<Void> block(@PathVariable Long id) {
        adminUserService.block(id);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/admin/users/{id}/unblock — 차단 해제. */
    @PostMapping("/{id}/unblock")
    public ResponseEntity<Void> unblock(@PathVariable Long id) {
        adminUserService.unblock(id);
        return ResponseEntity.noContent().build();
    }
}
