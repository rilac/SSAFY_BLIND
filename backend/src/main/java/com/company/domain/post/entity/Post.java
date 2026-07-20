package com.company.domain.post.entity;

import com.company.domain.comment.entity.Comment;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;              // [FEATURE:hidden-author-visibility]
import com.company.global.exception.InvalidStateException;   // [FEATURE:hidden-author-visibility]

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;                     // [FEATURE:hidden-author-visibility]

@Entity
@Table(name = "posts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    // TEXT 타입으로 긴 본문 저장
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // 카테고리 — 신규 글은 항상 지정(요청 검증 + 서비스에서 세팅).
    // 기존 데이터/ddl-auto update 호환을 위해 DB NOT NULL 제약은 두지 않는다.
    @Enumerated(EnumType.STRING)
    private PostCategory category;

    // 작성자 — DB에서는 연결하되, API 응답에서는 절대 노출하지 않음 (익명)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Builder.Default
    private int viewCount = 0;

    // 신고 누적으로 자동 숨김 처리된 글 — 일반 피드 비노출, 관리자만 열람.
    // 기존 데이터/ddl-auto update 호환을 위해 DB NOT NULL 제약은 두지 않는다.
    @Builder.Default
    private boolean hidden = false;

    // 관리자가 복원(검수 완료)한 글 — 재신고가 임계값을 넘어도 자동 숨김 대상에서 제외(재숨김 루프 방지, §1-1).
    // 기존 데이터/ddl-auto update 호환을 위해 DB NOT NULL 제약은 두지 않는다.
    @Builder.Default
    private boolean reviewed = false;

    // [FEATURE:pinned-posts] 관리자 공지 고정 — true면 피드에서 항상 최상단. 신규 글 기본 미고정. Flyway V6로 컬럼 추가.
    @Builder.Default
    private boolean pinned = false;
    // [/FEATURE:pinned-posts]

    // [FEATURE:qna-accept] QUESTION 글의 채택된 답변(댓글) id. null이면 미해결.
    // FK 없이 단순 Long — 채택 댓글 삭제 시 CommentService가 이 값을 정리한다(댕글링 방지).
    private Long acceptedCommentId;
    // [/FEATURE:qna-accept]

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // 댓글 양방향 관계 + cascade 삭제 — 게시글 삭제 시 연관 댓글도 자동 삭제
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // (#3) 게시글 수정 도메인 메서드 — @Setter 사용 금지, 반드시 이 메서드로만 변경
    public void update(String title, String content, PostCategory category) {
        this.title = title;
        this.content = content;
        this.category = category;
    }

    // 신고 누적 자동 숨김 / 관리자 복원 — @Setter 금지
    public void hide() {
        this.hidden = true;
    }

    // 관리자 복원 — 숨김 해제 + 검수 완료 표시(이후 재신고로 인한 재자동숨김 방지, §1-1)
    public void restore() {
        this.hidden = false;
        this.reviewed = true;
    }

    // [FEATURE:pinned-posts] 공지 고정 토글 — @Setter 금지, 도메인 메서드로만 변경. 관리자 액션에서만 호출.
    public void togglePin() {
        this.pinned = !this.pinned;
    }
    // [/FEATURE:pinned-posts]

    // [FEATURE:qna-accept] 답변 채택/해제 — @Setter 금지, 도메인 메서드로만 변경
    public void acceptAnswer(Long commentId) {
        this.acceptedCommentId = commentId;
    }

    public void clearAcceptedAnswer() {
        this.acceptedCommentId = null;
    }
    // [/FEATURE:qna-accept]

    // [FEATURE:hidden-author-visibility] 숨김 글 가시성/쓰기 게이트.
    // 게시글 상세·댓글·반응·스크랩·투표·신고가 각자 다른 기준을 쓰지 않도록 도메인에 단일화한다.

    /** 숨김 글을 볼 수 있는가 — 작성자 본인과 관리자만. 비로그인(viewerId=null)은 작성자일 수 없으므로 불가. */
    public boolean isVisibleTo(UserRole role, Long viewerId) {
        if (!this.hidden) {
            return true;
        }
        if (role == UserRole.ADMIN) {
            return true;
        }
        // author는 LAZY지만 hidden=true 경로에서만 접근하므로 목록 조회에 N+1을 만들지 않는다.
        return viewerId != null && this.author != null && viewerId.equals(this.author.getId());
    }

    /**
     * 볼 수 없으면 404. 숨김 사실 자체를 노출하지 않기 위해 403이 아니라 "존재하지 않는 게시글"로 처리한다
     * (기존 PostService.getPost 동작과 동일).
     */
    public void assertVisibleTo(UserRole role, Long viewerId) {
        if (!isVisibleTo(role, viewerId)) {
            throw new NoSuchElementException("존재하지 않는 게시글입니다.");
        }
    }

    /**
     * 숨김 글에는 누구도 새 활동을 남길 수 없다 — 작성자도 관리자도.
     *
     * <p>작성자를 막는 이유가 핵심이다: 신고로 숨겨진 글의 내용을 무해하게 갈아치우고 관리자가 복원하는
     * 검수 회피 경로를 차단한다. 관리자를 막는 이유는 (a) 글을 볼 수 없는 작성자에게 댓글 알림이 가고,
     * (b) 복원 시 검수 기간에 생긴 활동이 되살아나며, (c) 반응이 인기순 집계에 섞이기 때문이다.
     *
     * <p>가시성을 먼저 판정한다 — 순서를 뒤집으면 제3자가 400 메시지로 글의 존재와 모더레이션 상태를 알아낸다.
     */
    public void assertWritable(UserRole role, Long viewerId) {
        assertVisibleTo(role, viewerId);
        if (this.hidden) {
            throw new InvalidStateException("숨김 처리된 게시글에는 새 활동을 남길 수 없습니다.");
        }
    }
    // [/FEATURE:hidden-author-visibility]
}
