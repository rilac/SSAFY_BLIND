package com.company.domain.notification.service;

import com.company.domain.post.entity.Post;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// [FEATURE:weekly-digest] 주간 인기글 다이제스트 — 최근 7일 인기 Top N 글을 전체 ACTIVE 유저에게 앱 내 알림(SYSTEM)으로 발송.
// 익명 유지: 글 제목/링크만, 작성자 신원은 노출하지 않는다. 스케줄링은 WeeklyDigestScheduler가 담당.
@Service
@RequiredArgsConstructor
public class WeeklyDigestService {

    private static final Logger log = LoggerFactory.getLogger(WeeklyDigestService.class);

    // 집계 기간(최근 N일) — 스펙 고정 7일.
    private static final int WINDOW_DAYS = 7;
    // notifications.message는 varchar(255). 메시지에 노출할 제목 개수와 제목 미리보기 길이로 한도 내 유지하고,
    // 최종적으로 안전망 truncate한다.
    private static final int MESSAGE_MAX_LEN = 255;
    private static final int MESSAGE_TITLE_COUNT = 3;
    private static final int TITLE_PREVIEW_LEN = 30;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 메시지에 집계할 상위 글 개수(기본 5). app.weekly-digest.top-n으로 override.
    @Value("${app.weekly-digest.top-n:5}")
    private int topN;

    // 발송한 수신자 수를 반환(인기글이 없으면 0 — 발송 스킵).
    @Transactional
    public int sendWeeklyDigest() {
        LocalDateTime since = LocalDateTime.now().minusDays(WINDOW_DAYS);
        List<Post> topPosts = postRepository.findTopByScoreSince(since, PageRequest.of(0, topN));
        if (topPosts.isEmpty()) {
            log.info("주간 다이제스트: 최근 {}일 인기글이 없어 발송을 건너뜁니다", WINDOW_DAYS);
            return 0;
        }

        List<User> recipients = userRepository.findByStatus(UserStatus.ACTIVE);
        if (recipients.isEmpty()) {
            log.info("주간 다이제스트: ACTIVE 유저가 없어 발송을 건너뜁니다");
            return 0;
        }

        String message = buildMessage(topPosts);
        Long topPostId = topPosts.get(0).getId(); // 단일 링크 — 1위 글로 이동
        notificationService.notifyDigest(recipients, message, topPostId);
        return recipients.size();
    }

    // "지난 주 인기글 TOP3 — 1) 제목 / 2) 제목 / 3) 제목" 형태. 상위 MESSAGE_TITLE_COUNT개만 제목 노출(링크는 1위).
    private String buildMessage(List<Post> topPosts) {
        int count = Math.min(topPosts.size(), MESSAGE_TITLE_COUNT);
        StringBuilder sb = new StringBuilder("지난 주 인기글 TOP").append(count).append(" — ");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(" / ");
            sb.append(i + 1).append(") ").append(preview(topPosts.get(i).getTitle()));
        }
        return truncate(sb.toString());
    }

    private String preview(String title) {
        if (title == null) return "";
        return title.length() > TITLE_PREVIEW_LEN ? title.substring(0, TITLE_PREVIEW_LEN) + "..." : title;
    }

    // varchar(255) 안전망 — 정상 구성이면 한도 내라 거의 동작하지 않으나, 제목 길이 변동에 대비해 하드 컷.
    private String truncate(String message) {
        return message.length() > MESSAGE_MAX_LEN ? message.substring(0, MESSAGE_MAX_LEN) : message;
    }
}
// [/FEATURE:weekly-digest]
