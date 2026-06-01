package com.company.community.domain;

import jakarta.persistence.*;
import lombok.*;

// [FEATURE:poll] 익명 투표 보기 — 게시글에 부속(보기가 2개 이상이면 그 글은 "투표 글").
@Entity
@Table(name = "poll_options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PollOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(nullable = false)
    private String content;

    // 표시 순서(작성 시 입력 순)
    @Column(nullable = false)
    private int sortOrder;
}
