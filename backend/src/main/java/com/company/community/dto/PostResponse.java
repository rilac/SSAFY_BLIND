package com.company.community.dto;

import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import com.company.community.domain.User;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostResponse {

    private Long id;
    private PostCategory category;
    // 가명 작성자 정보(닉네임·기수·지역). 실제 신원은 미포함.
    private AuthorInfo author;
    private String title;
    private String content;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // (#3) isMine: 현재 유저가 작성자인지 — @JsonProperty로 직렬화 키 명시
    // Lombok boolean isMine → isMine() getter → Jackson "mine"으로 직렬화되는 문제 방지
    @JsonProperty("isMine")
    private boolean isMine;

    // (#4) 좋아요 정보
    @JsonProperty("isLiked")
    private boolean isLiked;

    private long likeCount;

    // 스크랩 여부
    @JsonProperty("isBookmarked")
    private boolean isBookmarked;

    // ★ 가명(닉네임·기수·지역)만 노출 — mmUserId/email 등 실제 신원은 포함하지 않는다.
    // author는 호출부에서 배치 조회한 작성자 User를 전달받는다.
    public static PostResponse of(Post post, Long currentUserId, boolean isLiked,
                                  long likeCount, boolean isBookmarked, User author) {
        return new PostResponse(
                post.getId(),
                post.getCategory(),
                AuthorInfo.of(author),
                post.getTitle(),
                post.getContent(),
                post.getViewCount(),
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getAuthor().getId().equals(currentUserId),
                isLiked,
                likeCount,
                isBookmarked
        );
    }
}
