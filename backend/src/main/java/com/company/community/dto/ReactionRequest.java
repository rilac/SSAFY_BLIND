package com.company.community.dto;

import com.company.community.domain.ReactionType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

// [FEATURE:reactions] 반응 토글 요청 — 누른 반응 종류.
@Getter
@NoArgsConstructor
public class ReactionRequest {

    @NotNull(message = "반응 종류를 선택해주세요.")
    private ReactionType type;
}
