package com.company.community.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// M-NEW-5: 조회수 서버단 중복 제거용 조회 이력.
// (post_id, user_id) 유니크 — 유저당 게시글 1행을 유지하며 최근 조회 시각만 갱신한다.
// 작성자 본인 조회는 서비스에서 애초에 기록/카운트하지 않는다.
@Entity
@Table(name = "post_views",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PostView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 마지막으로 조회수로 집계된 시각 — 이 시각 + dedup 윈도(24h) 이후 재조회만 다시 카운트.
    @Column(nullable = false)
    private LocalDateTime viewedAt;

    // 24h 경과 후 재조회 시 갱신 — @Setter 금지, 도메인 메서드로만 변경
    public void touch(LocalDateTime at) {
        this.viewedAt = at;
    }
}
