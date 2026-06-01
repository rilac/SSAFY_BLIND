package com.company.community.controller;

import com.company.community.domain.User;
import com.company.community.service.AccountService;
import com.company.community.util.CookieUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 계정 관리 — 휴면 전환 / 회원 탈퇴. 둘 다 처리 후 쿠키를 만료시켜 로그아웃 효과.
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CookieUtils cookieUtils;

    /**
     * POST /api/users/me/dormant — 휴면 전환 (재로그인 시 ACTIVE 복구)
     */
    @PostMapping("/dormant")
    public ResponseEntity<Void> goDormant(@AuthenticationPrincipal User user) {
        accountService.goDormant(user.getId());
        // 🗓️ 2026-06-02: access/refresh 쿠키 둘 다 만료
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredRefreshCookie().toString())
                .build();
    }

    /**
     * DELETE /api/users/me — 회원 탈퇴 (PII 익명화, 게시글/댓글 보존)
     */
    @DeleteMapping
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal User user) {
        accountService.withdraw(user.getId());
        // 🗓️ 2026-06-02: access/refresh 쿠키 둘 다 만료
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredAccessCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtils.createExpiredRefreshCookie().toString())
                .build();
    }
}
