package com.company.community.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

// [FEATURE:poll] 투표 요청 — 선택한 보기 id.
@Getter
@NoArgsConstructor
public class PollVoteRequest {

    @NotNull(message = "보기를 선택해주세요.")
    private Long optionId;
}
