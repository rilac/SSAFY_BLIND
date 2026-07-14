package com.company.domain.user.controller;

import com.company.domain.user.controller.dto.OnboardingRequest;
import com.company.domain.user.entity.User;
import com.company.domain.user.service.OnboardingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;

    /**
     * POST /api/onboarding
     * 닉네임/기수/캠퍼스 입력 → PENDING→ACTIVE 전환.
     * 쿠키 재발급 없음 — 필터가 매 요청 DB 상태를 재확인하므로 기존 팬텀 쿠키가 그대로 유효하다(R4).
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> completeOnboarding(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody OnboardingRequest request) {

        onboardingService.completeOnboarding(user.getId(), request);
        return ResponseEntity.ok(Map.of("message", "온보딩이 완료되었습니다."));
    }
}
