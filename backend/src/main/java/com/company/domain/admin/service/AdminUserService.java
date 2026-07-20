package com.company.domain.admin.service;

import com.company.domain.admin.controller.dto.AdminUserResponse;
import com.company.domain.auth.service.RefreshTokenService;
import com.company.domain.admin.entity.AdminAuditAction;
import com.company.domain.admin.entity.AdminAuditTargetType;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

// R8: 관리자 유저 관리 — 검색/상세/차단/차단해제. 차단 시 Redis 토큰(AT 팬텀+RT) 즉시 폐기로 강제 로그아웃.
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AdminStepUpService adminStepUpService;
    private final AdminAuditService adminAuditService;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> search(Long actorId, String keyword, String cohort, String campus,
                                          UserStatus status, Pageable pageable) {
        String kw = blankToNull(keyword);
        String co = blankToNull(cohort);
        String ca = blankToNull(campus);
        // 조회도 감사 대상 — 이 응답은 실계정(mmUserId/email)과 커뮤니티 가명을 한 레코드에 묶은
        // 역-익명화 조회 테이블이라, 익명 커뮤니티에서는 열람 자체가 추적돼야 한다.
        adminAuditService.record(actorId, AdminAuditAction.SEARCH_USERS,
                "keyword=" + kw + ", cohort=" + co + ", campus=" + ca + ", status=" + status);
        return userRepository.search(kw, co, ca, status, pageable).map(AdminUserResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(Long actorId, Long userId) {
        adminAuditService.record(actorId, AdminAuditAction.VIEW_USER,
                AdminAuditTargetType.USER, userId, null);
        return AdminUserResponse.from(loadUser(userId));
    }

    @Transactional
    public void block(Long actorId, Long userId) {
        User user = loadUser(userId);
        user.block();
        // 즉시 강제 로그아웃 — 모든 RT + 살아있는 AT 팬텀 폐기(deleteAllForUser가 둘 다 처리).
        refreshTokenService.deleteAllForUser(userId);
        // 차단 대상이 관리자였다면 살아있는 step-up 마커(15분)도 함께 폐기. 일반 유저는 no-op.
        adminStepUpService.clear(userId);
        adminAuditService.record(actorId, AdminAuditAction.BLOCK_USER,
                AdminAuditTargetType.USER, userId, null);
    }

    @Transactional
    public void unblock(Long actorId, Long userId) {
        loadUser(userId).unblock();
        adminAuditService.record(actorId, AdminAuditAction.UNBLOCK_USER,
                AdminAuditTargetType.USER, userId, null);
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
