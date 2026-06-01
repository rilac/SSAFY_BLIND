package com.company.community.repository;

import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    // ★ 조회수 동시성 해결 — 벌크 UPDATE로 race condition 방지
    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // 통합 목록 — 최신순. 모든 필터는 null-guard로 선택 적용.
    //  category: 카테고리 필터 / keyword: 제목·본문 검색
    //  authorId: scope=mine (내 글) / bookmarkerId: scope=bookmarked (내 스크랩)
    //  [FEATURE:cohort-campus-lounge] cohort: scope=cohort (동기) / campus: scope=campus (우리 캠퍼스)
    // 댓글수·좋아요수 등은 서비스에서 IN절 배치 조회 (N+1 방지)
    @Query(value = "SELECT p FROM Post p " +
            "WHERE p.hidden = false AND (:category IS NULL OR p.category = :category) " +
            "AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:authorId IS NULL OR p.author.id = :authorId) " +
            "AND (:bookmarkerId IS NULL OR EXISTS (SELECT b.id FROM Bookmark b WHERE b.post = p AND b.user.id = :bookmarkerId)) " +
            // [FEATURE:cohort-campus-lounge] 라운지: 같은 기수/캠퍼스 작성자 글로 한정(null이면 미적용)
            "AND (:cohort IS NULL OR p.author.cohort = :cohort) " +
            "AND (:campus IS NULL OR p.author.campus = :campus) " +
            // [/FEATURE:cohort-campus-lounge]
            "ORDER BY p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Post p " +
            "WHERE p.hidden = false AND (:category IS NULL OR p.category = :category) " +
            "AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:authorId IS NULL OR p.author.id = :authorId) " +
            "AND (:bookmarkerId IS NULL OR EXISTS (SELECT b.id FROM Bookmark b WHERE b.post = p AND b.user.id = :bookmarkerId)) " +
            // [FEATURE:cohort-campus-lounge]
            "AND (:cohort IS NULL OR p.author.cohort = :cohort) " +
            "AND (:campus IS NULL OR p.author.campus = :campus)")
            // [/FEATURE:cohort-campus-lounge]
    Page<Post> findFilteredLatest(@Param("category") PostCategory category,
                                  @Param("keyword") String keyword,
                                  @Param("authorId") Long authorId,
                                  @Param("bookmarkerId") Long bookmarkerId,
                                  @Param("cohort") String cohort,   // [FEATURE:cohort-campus-lounge]
                                  @Param("campus") String campus,   // [FEATURE:cohort-campus-lounge]
                                  Pageable pageable);

    // 통합 목록 — 인기순(좋아요 수 desc, 동률 시 최신). 필터는 위와 동일.
    @Query(value = "SELECT p FROM Post p LEFT JOIN PostLike pl ON pl.post = p " +
            "WHERE p.hidden = false AND (:category IS NULL OR p.category = :category) " +
            "AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:authorId IS NULL OR p.author.id = :authorId) " +
            "AND (:bookmarkerId IS NULL OR EXISTS (SELECT b.id FROM Bookmark b WHERE b.post = p AND b.user.id = :bookmarkerId)) " +
            // [FEATURE:cohort-campus-lounge]
            "AND (:cohort IS NULL OR p.author.cohort = :cohort) " +
            "AND (:campus IS NULL OR p.author.campus = :campus) " +
            // [/FEATURE:cohort-campus-lounge]
            "GROUP BY p ORDER BY COUNT(pl) DESC, p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Post p " +
            "WHERE p.hidden = false AND (:category IS NULL OR p.category = :category) " +
            "AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:authorId IS NULL OR p.author.id = :authorId) " +
            "AND (:bookmarkerId IS NULL OR EXISTS (SELECT b.id FROM Bookmark b WHERE b.post = p AND b.user.id = :bookmarkerId)) " +
            // [FEATURE:cohort-campus-lounge]
            "AND (:cohort IS NULL OR p.author.cohort = :cohort) " +
            "AND (:campus IS NULL OR p.author.campus = :campus)")
            // [/FEATURE:cohort-campus-lounge]
    Page<Post> findFilteredPopular(@Param("category") PostCategory category,
                                   @Param("keyword") String keyword,
                                   @Param("authorId") Long authorId,
                                   @Param("bookmarkerId") Long bookmarkerId,
                                   @Param("cohort") String cohort,   // [FEATURE:cohort-campus-lounge]
                                   @Param("campus") String campus,   // [FEATURE:cohort-campus-lounge]
                                   Pageable pageable);
}
