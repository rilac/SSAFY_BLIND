package com.company.domain.user.repository;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMmUserId(String mmUserId);

    boolean existsByMmUserId(String mmUserId);

    // [FEATURE:weekly-digest] 주간 다이제스트 수신 대상 — 상태별 유저 조회(ACTIVE 전체).
    List<User> findByStatus(UserStatus status);
    // [/FEATURE:weekly-digest]
}
