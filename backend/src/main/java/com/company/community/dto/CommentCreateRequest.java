package com.company.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CommentCreateRequest {

    @NotBlank(message = "댓글 내용을 입력해주세요.")
    @Size(max = 1000, message = "댓글은 1000자 이하로 입력해주세요.")
    private String content;

    // [FEATURE:nested-comments] 답글 대상(부모 댓글) id. null이면 최상위 댓글. 서버가 1-depth·소속을 검증.
    private Long parentId;
    // [/FEATURE:nested-comments]
}
