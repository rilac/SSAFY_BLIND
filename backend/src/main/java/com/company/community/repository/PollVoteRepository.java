package com.company.community.repository;

import com.company.community.domain.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// [FEATURE:poll] 투표 표 레포지토리
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    // 1인 1표 — 현재 유저의 표(있으면 변경/취소 판단)
    Optional<PollVote> findByPostIdAndUserId(Long postId, Long userId);

    // 보기별 표 수 집계 (익명 — 누가 골랐는지는 노출 안 함)
    @Query("SELECT v.optionId, COUNT(v) FROM PollVote v WHERE v.post.id = :postId GROUP BY v.optionId")
    List<Object[]> countByPostIdGroupByOption(@Param("postId") Long postId);

    // 게시글 삭제 전 정리(post_id FK)
    @Modifying
    @Query("DELETE FROM PollVote v WHERE v.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
