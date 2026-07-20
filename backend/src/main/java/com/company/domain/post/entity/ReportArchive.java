package com.company.domain.post.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 삭제된 게시글의 신고 기록 스냅샷 — 상습 위반자 추적용.
 *
 * <p>{@code reports}는 {@code post_id} FK 때문에 게시글 삭제 시 함께 사라진다. 관리자 검수 큐와 신고 통계가
 * 모두 {@code reports}에서만 파생되므로, 신고당한 글이 지워지면 사유·건수·신고자 기록이 흔적 없이 없어졌다.
 * 숨김 글 작성자에게 숨김 사실을 알려주기 시작하면 이 삭제가 "안내된 회피 경로"가 되므로 스냅샷을 남긴다.
 *
 * <p>글이 이미 없으므로 {@code posts}를 FK로 참조하지 않는다. {@code reporterId}도 FK를 두지 않아
 * 신고자가 탈퇴해도 집계가 유지된다. 조회 전용이라 상태 변경 메서드를 두지 않는다(구조적으로 immutable).
 */
@Entity
@Table(name = "report_archives")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ReportArchive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 삭제된 글의 id — FK 아님. 동일 글의 신고들을 사후에 묶어 보기 위한 값.
    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private String postTitle;

    // 신고당한 사람(글 작성자) — 상습 위반자 추적의 핵심 키.
    @Column(nullable = false)
    private Long authorId;

    @Column(nullable = false)
    private Long reporterId;

    @Enumerated(EnumType.STRING)
    private ReportReason reason;

    @Column(length = 200)
    private String detail;

    // 원래 신고 시각(reports.created_at)과 아카이브 시각을 모두 남긴다 — 사후 조사에서 순서가 중요하다.
    @Column(nullable = false)
    private LocalDateTime reportedAt;

    @Column(nullable = false)
    private LocalDateTime archivedAt;

    /** 삭제 직전의 Report에서 스냅샷을 만든다. */
    public static ReportArchive from(Report report, Post post, LocalDateTime archivedAt) {
        return ReportArchive.builder()
                .postId(post.getId())
                .postTitle(post.getTitle())
                .authorId(post.getAuthor().getId())
                .reporterId(report.getReporter().getId())
                .reason(report.getReason())
                .detail(report.getDetail())
                .reportedAt(report.getCreatedAt())
                .archivedAt(archivedAt)
                .build();
    }
}
