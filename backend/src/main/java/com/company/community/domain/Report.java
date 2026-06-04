package com.company.community.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// 게시글 신고 — 신고자별 게시글 1회. 유니크 제약으로 중복 신고 방지(멱등).
@Entity
@Table(name = "reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reporter_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // 신고 사유 — 신고 모달에서 선택
    @Enumerated(EnumType.STRING)
    private ReportReason reason;

    // [FEATURE:report-detail] 기타(ETC) 선택 시 신고자가 직접 적는 상세 사유(선택, 최대 200자). varchar라 enum 마이그레이션과 무관. Flyway V10.
    @Column(length = 200)
    private String detail;
    // [/FEATURE:report-detail]

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
