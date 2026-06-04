package com.company.domain.poll.entity;

import com.company.domain.post.entity.Post;
import com.company.domain.user.entity.User;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// [FEATURE:poll] 익명 투표 표 — (post_id, user_id) 유니크로 1인 1표.
// 표시는 보기별 집계만(누가 무엇을 골랐는지는 노출하지 않음 → 익명 유지).
@Entity
@Table(name = "poll_votes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PollVote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 선택한 보기 id — FK 없이 Long(글 삭제 시 서비스가 함께 정리). 집계는 option_id로 group by.
    @Column(nullable = false)
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // 재투표 — 선택 보기 변경(@Setter 금지, 도메인 메서드로만)
    public void changeOption(Long optionId) {
        this.optionId = optionId;
    }
}
