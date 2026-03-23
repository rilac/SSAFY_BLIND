package com.company.community.repository;

import com.company.community.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ★ N+1 해결 (#3) + 페이지네이션 (#10)
    // LEFT JOIN으로 댓글 수를 한 번의 쿼리로 가져옴
    @Query("SELECT p, COUNT(c) FROM Post p LEFT JOIN Comment c ON c.post = p " +
           "GROUP BY p ORDER BY p.createdAt DESC")
    Page<Object[]> findAllWithCommentCount(Pageable pageable);

    // ★ 조회수 동시성 해결 (#4)
    // 벌크 UPDATE로 race condition 방지
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);
}
