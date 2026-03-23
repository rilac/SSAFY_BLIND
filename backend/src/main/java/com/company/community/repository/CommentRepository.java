package com.company.community.repository;

import com.company.community.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 특정 게시글의 댓글을 오래된 순으로 조회
    List<Comment> findAllByPostIdOrderByCreatedAtAsc(Long postId);
}
