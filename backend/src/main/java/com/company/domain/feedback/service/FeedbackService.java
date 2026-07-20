package com.company.domain.feedback.service;

import com.company.domain.feedback.controller.dto.FeedbackRequest;
import com.company.domain.feedback.controller.dto.FeedbackResponse;
import com.company.domain.admin.entity.AdminAuditAction;
import com.company.domain.admin.entity.AdminAuditTargetType;
import com.company.domain.admin.service.AdminAuditService;
import com.company.domain.feedback.entity.Feedback;
import com.company.domain.feedback.entity.FeedbackStatus;
import com.company.domain.feedback.repository.FeedbackRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

// 건의함 — 작성(일반 사용자) / 전체 조회(관리자)
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final AdminAuditService adminAuditService;
    private final UserRepository userRepository;

    @Transactional
    public void create(Long userId, FeedbackRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
        feedbackRepository.save(Feedback.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .author(author)
                .build());
    }

    // processed=false → 미처리(PENDING)만, true → 처리됨(RESOLVED/REJECTED). 둘 다 최신순.
    @Transactional(readOnly = true)
    public List<FeedbackResponse> getByProcessed(boolean processed) {
        List<Feedback> list = processed
                ? feedbackRepository.findByStatusNotOrderByCreatedAtDesc(FeedbackStatus.PENDING)
                : feedbackRepository.findByStatusOrderByCreatedAtDesc(FeedbackStatus.PENDING);
        return list.stream()
                .map(FeedbackResponse::of)
                .collect(Collectors.toList());
    }

    // 관리자 처리 — 상태를 변경하고 갱신된 건의를 반환(처리/수용안함 시 기본 목록에서 제외됨).
    @Transactional
    public FeedbackResponse updateStatus(Long actorId, Long id, FeedbackStatus status) {
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 건의입니다."));
        feedback.changeStatus(status);
        adminAuditService.record(actorId, AdminAuditAction.UPDATE_FEEDBACK_STATUS,
                AdminAuditTargetType.FEEDBACK, id, "status=" + status);
        return FeedbackResponse.of(feedback);
    }
}
