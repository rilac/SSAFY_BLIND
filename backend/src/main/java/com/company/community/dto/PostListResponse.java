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
public class PostListResponse {

    private Long id;
    private PostCategory category;
    // 가명 작성자 정보(닉네임·기수·지역). 실제 신원은 미포함.
    private AuthorInfo author;
    private String title;
    private int viewCount;
    private int commentCount;
    private LocalDateTime createdAt;

    // (#3) isMine — 목록에서도 본인 글 표시
    @JsonProperty("isMine")
    private boolean isMine;

    // (#4) 좋아요 정보
    @JsonProperty("isLiked")
    private boolean isLiked;

    private long likeCount;

    // 스크랩 여부
    @JsonProperty("isBookmarked")
    private boolean isBookmarked;

    // [FEATURE:qna-accept] 해결됨 여부(채택된 답변 존재). 피드 카드 "해결됨" 배지용.
    @JsonProperty("solved")
    private boolean solved;
    // [/FEATURE:qna-accept]

    // [FEATURE:poll] 투표 포함 여부 — 피드 카드 "투표" 배지용. hasPoll은 호출부에서 배치 판별해 전달.
    @JsonProperty("hasPoll")
    private boolean hasPoll;
    // [/FEATURE:poll]

    // ★ 가명(닉네임·기수·지역)만 노출. author는 호출부에서 배치 조회한 작성자 User.
    public static PostListResponse of(Post post, long commentCount, Long currentUserId,
                                      boolean isLiked, long likeCount, boolean isBookmarked, User author,
                                      boolean hasPoll) {
        return new PostListResponse(
                post.getId(),
                post.getCategory(),
                AuthorInfo.of(author),
                post.getTitle(),
                post.getViewCount(),
                (int) commentCount,
                post.getCreatedAt(),
                post.getAuthor().getId().equals(currentUserId),
                isLiked,
                likeCount,
                isBookmarked,
                post.getAcceptedCommentId() != null, // [FEATURE:qna-accept] solved
                hasPoll // [FEATURE:poll]
        );
    }
}
