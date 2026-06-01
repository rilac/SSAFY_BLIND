package com.company.community.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter                    // ★ @Setter 사용 금지 (#8)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    // 어떤 게시글에 달린 댓글인지
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 작성자 — API 응답에서는 절대 노출하지 않음 (익명)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    // [FEATURE:nested-comments] 대댓글(1-depth) — 부모 댓글 id. null이면 최상위 댓글.
    // FK 없이 단순 Long(acceptedCommentId와 동일 방침) — 부모 삭제 시 CommentService가 답글을 정리한다.
    private Long parentId;
    // [/FEATURE:nested-comments]

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
