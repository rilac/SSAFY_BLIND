package com.company.domain.feedback.controller.dto;

import com.company.domain.feedback.entity.FeedbackStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// 관리자 건의 처리 요청 — { "status": "RESOLVED" | "REJECTED" }
@Getter
@Setter
@NoArgsConstructor
public class FeedbackStatusUpdateRequest {

    @NotNull
    private FeedbackStatus status;
}
