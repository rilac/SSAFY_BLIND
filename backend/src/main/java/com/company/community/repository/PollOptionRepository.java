package com.company.community.repository;

import com.company.community.domain.PollOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

// [FEATURE:poll] 투표 보기 레포지토리
public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

    // 단건 상세용 — 보기 목록(표시 순)
    List<PollOption> findByPostIdOrderBySortOrderAsc(Long postId);

    // N+1 방지 — 목록에서 "투표 있는 글" 판별용 post id 집합
    @Query("SELECT DISTINCT o.post.id FROM PollOption o WHERE o.post.id IN :postIds")
    List<Long> findPostIdsWithPoll(@Param("postIds") List<Long> postIds);

    // 게시글 삭제 전 정리(post_id FK)
    @Modifying
    @Query("DELETE FROM PollOption o WHERE o.post.id = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
