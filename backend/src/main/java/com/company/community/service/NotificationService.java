package com.company.community.service;

import com.company.community.domain.Notification;
import com.company.community.domain.NotificationType;
import com.company.community.domain.User;
import com.company.community.dto.NotificationResponse;
import com.company.community.exception.ForbiddenException;
import com.company.community.repository.NotificationRepository;
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

    public void notifyLike(User recipient, Long postId, String postTitle) {
        save(recipient, NotificationType.LIKE,
                "내 글이 좋아요를 받았어요: \"" + preview(postTitle) + "\"", postId);
    }

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
