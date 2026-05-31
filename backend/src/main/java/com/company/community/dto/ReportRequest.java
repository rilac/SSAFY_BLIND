package com.company.community.dto;

import com.company.community.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 신고 요청 — 신고 모달에서 사유를 선택해 전송
@Getter
@NoArgsConstructor
public class ReportRequest {

    @NotNull(message = "신고 사유를 선택해주세요.")
    private ReportReason reason;
}
