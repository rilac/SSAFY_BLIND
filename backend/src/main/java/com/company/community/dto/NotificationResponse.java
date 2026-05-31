package com.company.community.dto;

import com.company.community.domain.Notification;
import com.company.community.domain.NotificationType;
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
