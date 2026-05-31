package com.company.community.controller;

import com.company.community.domain.User;
import com.company.community.dto.OnboardingRequest;
import com.company.community.service.OnboardingService;
import com.company.community.util.CookieUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private final OnboardingService onboardingService;
    // (#5) CookieUtils를 @Component로 변경했으므로 인스턴스 주입
    private final CookieUtils cookieUtils;

    /**
     * POST /api/onboarding
     * 닉네임/부서 입력 → ACTIVE JWT 쿠키 재발급
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> completeOnboarding(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody OnboardingRequest request) {

        String newToken = onboardingService.completeOnboarding(user.getId(), request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createJwtCookie(newToken).toString())
                .body(Map.of("message", "온보딩이 완료되었습니다."));
    }
}
