package com.company.community.service;

import com.company.community.domain.User;
import com.company.community.exception.InvalidStateException;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 계정 — 휴면 전환 / 회원 탈퇴 (도메인 메서드로만 상태 변경)
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService; // 🗓️ 2026-06-02: 상태 변경 시 RT 전체 폐기

    @Transactional
    public void goDormant(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidStateException("존재하지 않는 유저입니다."));
        user.goDormant();
        // 🗓️ 2026-06-02: 휴면 계정의 모든 RT 폐기 → 재발급 불가(H-NEW-2 상태 재검증과 결합해 완전 무효화)
        refreshTokenService.deleteAllForUser(userId);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidStateException("존재하지 않는 유저입니다."));
        user.withdraw();
        // 🗓️ 2026-06-02: 탈퇴 계정의 모든 RT 폐기
        refreshTokenService.deleteAllForUser(userId);
    }
}
