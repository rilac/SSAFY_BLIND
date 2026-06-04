package com.company.domain.feedback.controller;

import com.company.domain.admin.controller.AdminController;
import com.company.domain.feedback.controller.dto.FeedbackRequest;
import com.company.domain.feedback.service.FeedbackService;
import com.company.domain.user.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// 건의함 작성 — 인증 사용자 누구나. 열람은 관리자(AdminController)만.
@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ResponseEntity<Void> create(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody FeedbackRequest request) {

        feedbackService.create(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
