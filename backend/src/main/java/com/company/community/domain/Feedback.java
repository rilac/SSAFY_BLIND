package com.company.community.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 건의함 — 관리자에게만 보이는 비밀 피드백/서비스 제안. 일반 피드에는 노출되지 않음.
@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 작성자 — 관리자 열람 시 가명(닉네임 등)으로 표시. 실제 신원은 응답에 미포함.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // 처리 상태 — 기본 PENDING. 관리자가 처리 완료/수용 안 함으로 바꾸면 기본 목록에서 숨겨진다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private FeedbackStatus status = FeedbackStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 관리자 처리 상태 변경
    public void changeStatus(FeedbackStatus status) {
        this.status = status;
    }
}
