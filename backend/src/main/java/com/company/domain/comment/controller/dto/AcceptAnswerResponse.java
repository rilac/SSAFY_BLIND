package com.company.domain.comment.controller.dto;

// [FEATURE:qna-accept] 답변 채택 토글 결과 — 채택된 댓글 id(없으면 null) + 해결 여부.
public record AcceptAnswerResponse(Long acceptedCommentId, boolean solved) {

    public static AcceptAnswerResponse from(Long acceptedCommentId) {
        return new AcceptAnswerResponse(acceptedCommentId, acceptedCommentId != null);
    }
}
// [/FEATURE:qna-accept]
