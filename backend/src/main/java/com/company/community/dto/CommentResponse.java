package com.company.community.dto;

import com.company.community.domain.Comment;
import com.company.community.domain.User;
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

    // author는 호출부에서 배치 조회한 작성자 User를 전달받는다.
    public static CommentResponse of(Comment comment, Long currentUserId, User author) {
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                AuthorInfo.of(author),
                comment.getAuthor().getId().equals(currentUserId)
        );
    }
}
