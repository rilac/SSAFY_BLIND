package com.company.domain.notification.controller;

import com.company.domain.notification.controller.dto.NotificationResponse;
import com.company.domain.notification.service.NotificationService;
import com.company.domain.user.entity.User;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * GET /api/notifications — 내 알림 목록 (최신순)
     */
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> list(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(notificationService.getNotifications(user.getId()));
    }

    /**
     * PATCH /api/notifications/{id}/read — 단일 알림 읽음 처리
     */
    @PatchMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.markRead(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/notifications/read-all — 전체 읽음 처리
     */
    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }
}
