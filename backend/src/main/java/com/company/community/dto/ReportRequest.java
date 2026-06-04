package com.company.community.dto;

import com.company.community.domain.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 신고 요청 — 신고 모달에서 사유를 선택해 전송
@Getter
@NoArgsConstructor
public class ReportRequest {

    @NotNull(message = "신고 사유를 선택해주세요.")
    private ReportReason reason;

    // [FEATURE:report-detail] 기타(ETC) 선택 시 직접 입력하는 상세 사유(선택). 서버 @Size가 최종 방어선(프론트도 maxLength 200).
    @Size(max = 200, message = "상세 사유는 200자 이하로 입력해주세요.")
    private String detail;
    // [/FEATURE:report-detail]
}
