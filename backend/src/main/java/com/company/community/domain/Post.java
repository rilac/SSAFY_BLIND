package com.company.community.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    public void restore() {
        this.hidden = false;
    }
}
