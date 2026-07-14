package com.company.domain.admin.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

// R8: 관리자 페이지 2차 인증(step-up) 요청 — 관리자 코드.
@Getter
@NoArgsConstructor
public class AdminVerifyRequest {

    @NotBlank(message = "관리자 코드를 입력해주세요.")
    private String code;
}
