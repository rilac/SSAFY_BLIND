package com.company.domain.post.entity;

import com.company.domain.user.entity.User;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// (#4) 게시글 좋아요 엔티티 — 유니크 제약으로 중복 좋아요 방지
@Entity
@Table(name = "post_likes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostLike {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // [FEATURE:reactions] 반응 종류(좋아요/도움돼요/정보/공감). (post_id,user_id) 유니크라 1인 1반응.
    // 기존 좋아요 행은 V5 마이그레이션에서 LIKE로 채워진다.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReactionType reactionType = ReactionType.LIKE;
    // [/FEATURE:reactions]

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // [FEATURE:reactions] 다른 반응으로 변경 — @Setter 금지, 도메인 메서드로만.
    public void changeType(ReactionType reactionType) {
        this.reactionType = reactionType;
    }
    // [/FEATURE:reactions]
}
