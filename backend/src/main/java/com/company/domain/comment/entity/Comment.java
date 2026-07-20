package com.company.domain.comment.entity;

import com.company.domain.post.entity.Post;
import com.company.domain.user.entity.User;

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

    // 본문 최대 1000자 — CommentCreateRequest.@Size(max = 1000) 및 comments.content varchar(1000)(V15)과 일치.
    // length를 생략하면 Hibernate 기본값 255로 매핑되어 256자↑ 댓글이 INSERT 단계에서 잘림 오류(→ 409)가 된다.
    // 명시해야 ddl-auto=validate가 DTO 상한과 스키마의 재이탈을 기동 시점에 잡는다.
    @Column(nullable = false, length = 1000)
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
