package com.company.domain.post.repository;

import com.company.domain.comment.entity.Comment;
import com.company.domain.post.entity.Bookmark;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.entity.PostLike;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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
            "WHERE (:includeHidden = true OR p.hidden = false) AND (:category IS NULL OR p.category = :category) " +
            "AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:authorId IS NULL OR p.author.id = :authorId) " +
            "AND (:bookmarkerId IS NULL OR EXISTS (SELECT b.id FROM Bookmark b WHERE b.post = p AND b.user.id = :bookmarkerId)) " +
            // [FEATURE:cohort-campus-lounge] 라운지: 같은 기수/캠퍼스 작성자 글로 한정(null이면 미적용)
            "AND (:cohort IS NULL OR p.author.cohort = :cohort) " +
            "AND (:campus IS NULL OR p.author.campus = :campus) " +
            // [/FEATURE:cohort-campus-lounge]
            // [FEATURE:pinned-posts] 공지 고정 글을 항상 최상단(필터 결과 내). 동순위는 기존 최신순.
            "ORDER BY p.pinned DESC, p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Post p " +
            "WHERE (:includeHidden = true OR p.hidden = false) AND (:category IS NULL OR p.category = :category) " +
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
                                  @Param("includeHidden") boolean includeHidden, // [FEATURE:admin-moderation] ADMIN이면 숨김 글 포함
                                  Pageable pageable);

    // 통합 목록 — 인기순(좋아요 수 desc, 동률 시 최신). 필터는 위와 동일.
    @Query(value = "SELECT p FROM Post p LEFT JOIN PostLike pl ON pl.post = p " +
            "WHERE (:includeHidden = true OR p.hidden = false) AND (:category IS NULL OR p.category = :category) " +
            "AND (:keyword IS NULL OR p.title LIKE CONCAT('%', :keyword, '%') OR p.content LIKE CONCAT('%', :keyword, '%')) " +
            "AND (:authorId IS NULL OR p.author.id = :authorId) " +
            "AND (:bookmarkerId IS NULL OR EXISTS (SELECT b.id FROM Bookmark b WHERE b.post = p AND b.user.id = :bookmarkerId)) " +
            // [FEATURE:cohort-campus-lounge]
            "AND (:cohort IS NULL OR p.author.cohort = :cohort) " +
            "AND (:campus IS NULL OR p.author.campus = :campus) " +
            // [/FEATURE:cohort-campus-lounge]
            // [FEATURE:pinned-posts] 공지 고정 글을 항상 최상단(필터 결과 내). 동순위는 기존 인기순(좋아요수→최신).
            "GROUP BY p ORDER BY p.pinned DESC, COUNT(pl) DESC, p.createdAt DESC",
           countQuery = "SELECT COUNT(p) FROM Post p " +
            "WHERE (:includeHidden = true OR p.hidden = false) AND (:category IS NULL OR p.category = :category) " +
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
                                   @Param("includeHidden") boolean includeHidden, // [FEATURE:admin-moderation] ADMIN이면 숨김 글 포함
                                   Pageable pageable);

    // [FEATURE:weekly-digest] 주간 다이제스트 — 최근 N일(:since 이후 작성) 인기 글 랭킹.
    // 점수 = 조회수*1 + 반응수*2 + 댓글수*3 (조회:반응:댓글 = 1:2:3) 내림차순, 동점이면 최신. Pageable로 Top N.
    // ⚠️ 다중 LEFT JOIN의 카티전 곱을 막기 위해 COUNT은 반드시 DISTINCT. reactions = post_likes 전체 행(반응 종류 무관).
    @Query("SELECT p FROM Post p " +
            "LEFT JOIN PostLike pl ON pl.post = p " +
            "LEFT JOIN Comment c ON c.post = p " +
            "WHERE p.hidden = false AND p.createdAt >= :since " +
            "GROUP BY p " +
            "ORDER BY (p.viewCount + COUNT(DISTINCT pl) * 2 + COUNT(DISTINCT c) * 3) DESC, p.createdAt DESC")
    List<Post> findTopByScoreSince(@Param("since") LocalDateTime since, Pageable pageable);
    // [/FEATURE:weekly-digest]
}
