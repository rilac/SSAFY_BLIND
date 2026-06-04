package com.company.domain.feedback.repository;

import com.company.domain.feedback.entity.Feedback;
import com.company.domain.feedback.entity.FeedbackStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // 미처리(PENDING) 목록 — 기본 화면.
    List<Feedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status);

    // 처리됨(PENDING 아님 = RESOLVED/REJECTED) 목록 — '처리된 건의 보기' 토글.
    List<Feedback> findByStatusNotOrderByCreatedAtDesc(FeedbackStatus status);
}
