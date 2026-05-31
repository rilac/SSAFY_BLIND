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

    @Transactional
    public void goDormant(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidStateException("존재하지 않는 유저입니다."));
        user.goDormant();
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidStateException("존재하지 않는 유저입니다."));
        user.withdraw();
    }
}
