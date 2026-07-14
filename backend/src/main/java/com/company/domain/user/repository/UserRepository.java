package com.company.domain.user.repository;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMmUserId(String mmUserId);

    boolean existsByMmUserId(String mmUserId);

    // [FEATURE:weekly-digest] 주간 다이제스트 수신 대상 — 상태별 유저 조회(ACTIVE 전체).
    List<User> findByStatus(UserStatus status);
    // [/FEATURE:weekly-digest]

    // R8: 관리자 유저 검색 — keyword(MM계정/이메일/닉네임 부분일치) + cohort/campus/status 필터(각각 null이면 미적용).
    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL
                   OR LOWER(u.nickname)   LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.mmUsername) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email)      LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:cohort IS NULL OR u.cohort = :cohort)
              AND (:campus IS NULL OR u.campus = :campus)
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> search(@Param("keyword") String keyword,
                      @Param("cohort") String cohort,
                      @Param("campus") String campus,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
