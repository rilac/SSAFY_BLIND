package com.company.community.repository;

import com.company.community.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 중복 신고 방지 (멱등 처리)
    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);

    // 누적 신고 수 — 자동 숨김 임계값 판정
    long countByPostId(Long postId);

    // 관리자 검수: 사유 집계 / 신고된 게시글 목록 산출
    List<Report> findByPostId(Long postId);

    List<Report> findAllByOrderByCreatedAtDesc();

    // C-NEW-1: 게시글 삭제 전 자식(신고) 정리 — FK 제약 위반 방지
    @Modifying
    @Query("DELETE FROM Report r WHERE r.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
