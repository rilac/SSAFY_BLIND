package com.company.domain.comment.controller.dto;

import com.company.domain.comment.entity.Comment;
import com.company.domain.comment.service.CommentService;
import com.company.domain.user.entity.User;
import com.company.global.dto.AuthorInfo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class CommentResponse {

    private Long id;
    private String content;
    private LocalDateTime createdAt;
    // 가명 작성자 정보(닉네임·기수·지역). 실제 신원은 미포함.
    private AuthorInfo author;

    // (#2) isMine: 현재 유저가 이 댓글의 작성자인지
    @JsonProperty("isMine")
    private boolean isMine;

    // [FEATURE:op-alias] 글 단위 일관 별칭(글쓴이/익명N) — 스레드 내 신원 가독성용. 닉네임 대신 노출.
    private String alias;
    // 이 댓글 작성자가 글쓴이(OP)인지. 필드명을 op로 둔 이유: boolean isAuthor는 author(AuthorInfo) 게터와
    // 충돌해 Lombok이 게터를 생성하지 않는다. JSON 키는 @JsonProperty로 "isAuthor"에 고정(프론트 계약 유지).
    @JsonProperty("isAuthor")
    private boolean op;
    // [/FEATURE:op-alias]

    // [FEATURE:nested-comments] 부모 댓글 id(대댓글). null이면 최상위 — 프론트가 답글을 부모 아래에 그룹핑.
    private Long parentId;
    // [/FEATURE:nested-comments]

    // [FEATURE:comment-likes] 댓글 좋아요 수 + 현재 유저의 좋아요 여부.
    private long likeCount;
    @JsonProperty("isLiked")
    private boolean isLiked;
    // [/FEATURE:comment-likes]

    // author는 호출부에서 배치 조회한 작성자 User를 전달받는다.
    // [FEATURE:op-alias] alias(글쓴이/익명N) + isAuthor(글쓴이 여부)는 호출부(CommentService)에서 글 단위로 계산해 전달.
    public static CommentResponse of(Comment comment, Long currentUserId, User author,
                                     String alias, boolean isAuthor,
                                     long likeCount, boolean isLiked) { // [FEATURE:comment-likes]
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                AuthorInfo.of(author),
                comment.getAuthor().getId().equals(currentUserId),
                alias,
                isAuthor,
                comment.getParentId(), // [FEATURE:nested-comments]
                likeCount, isLiked // [FEATURE:comment-likes]
        );
    }
    // [/FEATURE:op-alias]
}
