package com.company.domain.comment.controller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

// [FEATURE:comment-likes] 댓글 좋아요 토글 응답 — 새 상태(liked) + 갱신된 좋아요 수.
@Getter
@AllArgsConstructor
public class CommentLikeResponse {

    @JsonProperty("liked")
    private boolean liked;

    private long likeCount;
}
