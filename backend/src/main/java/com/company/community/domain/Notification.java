package com.company.community.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 알림 — 수신자(recipient)에게 도착하는 활동 알림. 작성자(actor) 정보는 저장하지 않음(익명).
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림 수신자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String message;

    // 연결된 게시글(있으면 클릭 시 상세 이동). 시스템 알림 등은 null 가능.
    private Long postId;

    @Column(nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 읽음 처리 — @Setter 금지, 도메인 메서드로만 변경
    public void markRead() {
        this.isRead = true;
    }
}
