package com.company.domain.notification.controller.dto;

import com.company.domain.notification.entity.Notification;
import com.company.domain.notification.entity.NotificationType;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private NotificationType type;
    private String message;
    private Long postId;

    @JsonProperty("isRead")
    private boolean isRead;

    private LocalDateTime createdAt;

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType(),
                n.getMessage(),
                n.getPostId(),
                n.isRead(),
                n.getCreatedAt()
        );
    }
}
