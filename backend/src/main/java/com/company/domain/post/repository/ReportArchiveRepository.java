package com.company.domain.post.repository;

import com.company.domain.post.entity.ReportArchive;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// 삭제된 게시글의 신고 스냅샷 — 상습 위반자 조회용. 삭제 메서드를 두지 않는다(append-only).
public interface ReportArchiveRepository extends JpaRepository<ReportArchive, Long> {

    // 특정 회원이 과거에 몇 번 신고당했는지 — 제재 판단 근거.
    List<ReportArchive> findByAuthorIdOrderByArchivedAtDesc(Long authorId);

    long countByAuthorId(Long authorId);
}
