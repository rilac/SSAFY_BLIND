package com.company.community.repository;

import com.company.community.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByMmUserId(String mmUserId);

    boolean existsByMmUserId(String mmUserId);
}
