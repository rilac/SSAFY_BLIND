package com.company.domain.user.service;

import com.company.domain.user.controller.dto.OnboardingRequest;
import com.company.domain.user.entity.OnboardingOptions;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidStateException;
import com.company.global.security.JwtProvider;

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

        // 서버단 화이트리스트 검증 — 프론트 선택지를 우회한 직접 API 호출 차단(인증 우회가 아니라 입력 검증 누락 보완).
        // @NotBlank/@Size(DTO)는 1차 방어, 여기서 cohort/campus/nickname을 OnboardingOptions와 대조한다.
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        String cohort = request.getCohort() == null ? "" : request.getCohort().trim();
        String campus = request.getCampus() == null ? "" : request.getCampus().trim();
        if (!OnboardingOptions.isValidNickname(nickname)) {
            throw new InvalidStateException("닉네임 형식이 올바르지 않습니다.");
        }
        if (!OnboardingOptions.COHORTS.contains(cohort)) {
            throw new InvalidStateException("유효하지 않은 기수입니다.");
        }
        if (!OnboardingOptions.CAMPUSES.contains(campus)) {
            throw new InvalidStateException("유효하지 않은 캠퍼스입니다.");
        }

        // 도메인 메서드로 상태 변경 — @Setter 사용 금지. 검증·정규화(trim)된 값으로 저장.
        user.completeOnboarding(nickname, cohort, campus);

        // (#1) ACTIVE 상태 + 기존 role을 유지하여 새 JWT 발급
        return jwtProvider.generateToken(user.getId(), UserStatus.ACTIVE, user.getRole());
    }
}
