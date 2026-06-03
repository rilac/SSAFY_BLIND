package com.company.community.dto;

import com.company.community.domain.FeedbackStatus;
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
