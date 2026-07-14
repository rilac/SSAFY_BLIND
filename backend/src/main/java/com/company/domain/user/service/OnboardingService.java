package com.company.domain.user.service;

import com.company.domain.user.controller.dto.OnboardingRequest;
import com.company.domain.user.entity.OnboardingOptions;
import com.company.domain.user.entity.User;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidStateException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OnboardingService {

    private final UserRepository userRepository;

    /**
     * 온보딩 완료: 닉네임/기수/캠퍼스 설정 → PENDING→ACTIVE 전환.
     *
     * <p>토큰 재발급은 하지 않는다 — JwtAuthFilter가 매 요청 DB 상태를 재확인하므로
     * 로그인 시 발급된 팬텀 쿠키가 ACTIVE 전환 즉시 그대로 유효하다. (팬텀 토큰 개편 전에는
     * 여기서 새 JWT를 쿠키로 재발급했는데, 그대로 두면 원문 JWT가 클라이언트에 노출되고
     * 팬텀이 아닌 쿠키는 Redis 해석에 실패해 온보딩 직후 세션이 끊긴다.)
     */
    @Transactional
    public void completeOnboarding(Long userId, OnboardingRequest request) {
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
    }
}
