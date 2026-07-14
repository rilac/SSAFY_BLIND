package com.company.domain.admin.controller;

import com.company.domain.admin.controller.dto.AdminVerifyRequest;
import com.company.domain.admin.service.AdminStepUpService;
import com.company.domain.user.entity.User;
import com.company.global.exception.ForbiddenException;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// R8: 관리자 2차 인증(step-up) — 코드 검증 성공 시 15분 마커 부여. 이 엔드포인트는 step-up 없이도 접근(ROLE_ADMIN만).
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminStepUpService adminStepUpService;

    /** POST /api/admin/verify — 관리자 코드 검증. 성공 시 15분 step-up 마커. 실패 시 403. */
    @PostMapping("/verify")
    public ResponseEntity<Void> verify(@AuthenticationPrincipal User user,
                                       @Valid @RequestBody AdminVerifyRequest request) {
        if (!adminStepUpService.verify(user.getId(), request.getCode())) {
            throw new ForbiddenException("관리자 인증 코드가 올바르지 않습니다.");
        }
        return ResponseEntity.noContent().build();
    }
}
