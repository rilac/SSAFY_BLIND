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

    // [FEATURE:qna-accept] 채택된 답변(댓글) id. null이면 미해결. QUESTION 글에서만 의미.
    private Long acceptedCommentId;
    // [/FEATURE:qna-accept]

    // [FEATURE:poll] 익명 투표 결과(보기/집계/내 선택). 투표 없는 글이면 null.
    private PollResponse poll;
    // [/FEATURE:poll]

    // ★ 가명(닉네임·기수·지역)만 노출 — mmUserId/email 등 실제 신원은 포함하지 않는다.
    // author는 호출부에서 배치 조회한 작성자 User를 전달받는다. poll은 호출부(PostService)에서 PollService로 계산해 전달(투표 없으면 null).
    public static PostResponse of(Post post, Long currentUserId, boolean isLiked,
                                  long likeCount, boolean isBookmarked, User author, PollResponse poll) {
        return of(post, currentUserId, isLiked, likeCount, isBookmarked, author, post.getViewCount(), poll);
    }

    // M-NEW-5: 조회수를 명시적으로 전달하는 변형.
    // getPost는 조회수를 벌크 UPDATE(원자적)로 올리므로 관리 엔티티의 viewCount는 갱신 전 값이다.
    // 엔티티를 직접 변경하면 dirty checking으로 2중 증가하므로, 표시값만 +1 하여 여기로 넘긴다.
    public static PostResponse of(Post post, Long currentUserId, boolean isLiked,
                                  long likeCount, boolean isBookmarked, User author, int viewCount,
                                  PollResponse poll) {
        return new PostResponse(
                post.getId(),
                post.getCategory(),
                AuthorInfo.of(author),
                post.getTitle(),
                post.getContent(),
                viewCount,
                post.getCreatedAt(),
                post.getUpdatedAt(),
                post.getAuthor().getId().equals(currentUserId),
                isLiked,
                likeCount,
                isBookmarked,
                post.getAcceptedCommentId(), // [FEATURE:qna-accept]
                poll // [FEATURE:poll]
        );
    }
}
