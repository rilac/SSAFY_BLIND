package com.company.domain.notification.service;

import com.company.domain.notification.controller.dto.NotificationResponse;
import com.company.domain.notification.entity.Notification;
import com.company.domain.notification.entity.NotificationType;
import com.company.domain.notification.repository.NotificationRepository;
import com.company.domain.user.entity.User;
import com.company.global.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int TITLE_PREVIEW_LEN = 20;

    private final NotificationRepository notificationRepository;

    // ── 알림 생성 (이벤트 발생 시 다른 서비스에서 호출) ──

    public void notifyComment(User recipient, Long postId, String postTitle) {
        save(recipient, NotificationType.COMMENT,
                "내 글에 새 댓글이 달렸어요: \"" + preview(postTitle) + "\"", postId);
    }

    // [FEATURE:nested-comments] 답글 알림 — 부모 댓글 작성자에게. enum 마이그레이션을 피하려 COMMENT 타입 재사용,
    // 메시지로 "답글"을 구분(프론트는 postId로 이동).
    public void notifyReply(User recipient, Long postId) {
        save(recipient, NotificationType.COMMENT, "내 댓글에 답글이 달렸어요", postId);
    }
    // [/FEATURE:nested-comments]

    // [FEATURE:reactions] 반응 알림(좋아요 포함 4종 공통). NotificationType.LIKE 재사용 — enum 마이그레이션 회피.
    public void notifyReaction(User recipient, Long postId, String postTitle) {
        save(recipient, NotificationType.LIKE,
                "내 글에 반응이 달렸어요: \"" + preview(postTitle) + "\"", postId);
    }
    // [/FEATURE:reactions]

    // [FEATURE:weekly-digest] 주간 인기글 다이제스트 — 전체 수신자에게 동일한 SYSTEM 알림을 1건씩 일괄 생성.
    // 유저 수만큼 INSERT가 발생하므로 saveAll로 배치 저장(개별 save N회 회피). message 길이 제한(varchar 255)은
    // 호출측(WeeklyDigestService)에서 구성 시 보장하고, topPostId는 단일 Long이라 1위 글로 링크된다.
    public void notifyDigest(List<User> recipients, String message, Long topPostId) {
        List<Notification> notifications = recipients.stream()
                .map(recipient -> Notification.builder()
                        .recipient(recipient)
                        .type(NotificationType.SYSTEM)
                        .message(message)
                        .postId(topPostId)
                        .build())
                .collect(Collectors.toList());
        notificationRepository.saveAll(notifications);
    }
    // [/FEATURE:weekly-digest]

    private void save(User recipient, NotificationType type, String message, Long postId) {
        notificationRepository.save(Notification.builder()
                .recipient(recipient)
                .type(type)
                .message(message)
                .postId(postId)
                .build());
    }

    private String preview(String title) {
        if (title == null) return "";
        return title.length() > TITLE_PREVIEW_LEN ? title.substring(0, TITLE_PREVIEW_LEN) + "..." : title;
    }

    // ── 조회/읽음 처리 ──

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 알림입니다."));
        if (!n.getRecipient().getId().equals(userId)) {
            throw new ForbiddenException("본인의 알림만 읽음 처리할 수 있습니다.");
        }
        n.markRead(); // 도메인 메서드 — dirty checking으로 반영
    }

    @Transactional
    public void markAllRead(Long userId) {
        notificationRepository.markAllRead(userId);
    }
}
