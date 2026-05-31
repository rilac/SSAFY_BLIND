package com.company.community.service;

import com.company.community.domain.User;
import com.company.community.domain.UserStatus;
import com.company.community.dto.OnboardingRequest;
import com.company.community.exception.InvalidStateException;
import com.company.community.repository.UserRepository;
import com.company.community.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * 온보딩 완료: 닉네임/기수/캠퍼스 설정 → PENDING→ACTIVE 전환 → 새 JWT 발급
     */
    @Transactional
    public String completeOnboarding(Long userId, OnboardingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidStateException("존재하지 않는 유저입니다."));

        // 도메인 메서드로 상태 변경 — @Setter 사용 금지
        user.completeOnboarding(request.getNickname(), request.getCohort(), request.getCampus());

        // (#1) ACTIVE 상태 + 기존 role을 유지하여 새 JWT 발급
        return jwtProvider.generateToken(user.getId(), UserStatus.ACTIVE, user.getRole());
    }
}
