package com.company.domain.comment.repository;

import com.company.domain.comment.entity.Comment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 특정 게시글의 댓글을 오래된 순으로 조회
    List<Comment> findAllByPostIdOrderByCreatedAtAsc(Long postId);

    // N+1 방지 — 게시글 ID 리스트에 대한 댓글 수를 한 번에 집계 (목록 조회용)
    @Query("SELECT c.post.id, COUNT(c) FROM Comment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<Long> postIds);

    // [FEATURE:nested-comments] 부모 댓글 삭제 시 그 답글들을 한 번에 정리(1-depth라 답글은 자식이 없음).
    @Modifying
    @Query("DELETE FROM Comment c WHERE c.parentId = :parentId")
    void deleteByParentId(@Param("parentId") Long parentId);
    // [/FEATURE:nested-comments]
}
