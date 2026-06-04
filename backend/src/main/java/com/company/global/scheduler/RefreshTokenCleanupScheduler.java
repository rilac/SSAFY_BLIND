package com.company.global.scheduler;

import com.company.domain.auth.entity.RefreshToken;
import com.company.domain.auth.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 🗓️ 2026-06-02: 만료된 Refresh Token 주기 정리.
// 회전/로그아웃으로 즉시 삭제되는 것 외에 자연 만료된 RT가 누적되므로 벌크 삭제 잡이 필요하다.
// ⚠️ 단일 인스턴스 전제 — 수평 확장 시 ShedLock 등으로 중복 실행을 막아야 한다.
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

    private final RefreshTokenService refreshTokenService;

    // 기본 매일 04:00 — app.refresh-cleanup.cron 으로 override 가능
    @Scheduled(cron = "${app.refresh-cleanup.cron:0 0 4 * * *}")
    public void cleanupExpired() {
        int deleted = refreshTokenService.deleteExpired();
        if (deleted > 0) {
            log.info("만료된 RefreshToken {}건 정리 완료", deleted);
        }
    }
}
