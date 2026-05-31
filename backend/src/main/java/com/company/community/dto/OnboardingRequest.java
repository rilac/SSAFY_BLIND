package com.company.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OnboardingRequest {

    @NotBlank(message = "닉네임을 입력해주세요.")
    private String nickname;

    @NotBlank(message = "기수를 선택해주세요.")
    private String cohort;

    @NotBlank(message = "캠퍼스를 선택해주세요.")
    private String campus;
}
