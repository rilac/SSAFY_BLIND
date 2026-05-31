package com.company.community.repository;

import com.company.community.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportRepository extends JpaRepository<Report, Long> {

    // 중복 신고 방지 (멱등 처리)
    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);

    // 누적 신고 수 — 자동 숨김 임계값 판정
    long countByPostId(Long postId);

    // 관리자 검수: 사유 집계 / 신고된 게시글 목록 산출
    List<Report> findByPostId(Long postId);

    List<Report> findAllByOrderByCreatedAtDesc();
}
