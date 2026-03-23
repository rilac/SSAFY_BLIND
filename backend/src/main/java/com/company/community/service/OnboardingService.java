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
     * 온보딩 완료: 닉네임/부서 설정 → PENDING→ACTIVE 전환 → 새 JWT 발급
     */
    @Transactional
    public String completeOnboarding(Long userId, OnboardingRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidStateException("존재하지 않는 유저입니다."));

        // ★ setter 대신 도메인 메서드 사용 (#8)
        // 상태 검증 로직이 User 엔티티 내부에 캡슐화됨
        user.completeOnboarding(request.getNickname(), request.getDepartment());

        // ACTIVE 상태가 반영된 새 JWT 발급
        return jwtProvider.generateToken(user.getId(), UserStatus.ACTIVE);
    }
}
