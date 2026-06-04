package com.company.global.scheduler;

import com.company.domain.notification.service.WeeklyDigestService;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// [FEATURE:weekly-digest] 🗓️ 주간 인기글 다이제스트 — 매주 월요일 09:10 지난 7일 인기글 Top N을 전체 ACTIVE 유저에게 알림 발송.
// ⚠️ 단일 인스턴스 전제 — 수평 확장 시 ShedLock 등으로 중복 실행을 막아야 한다(RefreshTokenCleanupScheduler와 동일 방침).
@Component
@RequiredArgsConstructor
public class WeeklyDigestScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestScheduler.class);

    private final WeeklyDigestService weeklyDigestService;

    // 기본 매주 월 09:10(0 10 9 * * MON) — app.weekly-digest.cron 으로 override 가능
    @Scheduled(cron = "${app.weekly-digest.cron:0 10 9 * * MON}")
    public void sendWeeklyDigest() {
        int recipients = weeklyDigestService.sendWeeklyDigest();
        if (recipients > 0) {
            log.info("주간 인기글 다이제스트 발송 완료 — 수신자 {}명", recipients);
        }
    }
}
// [/FEATURE:weekly-digest]
