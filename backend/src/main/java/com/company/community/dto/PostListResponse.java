package com.company.community.dto;

import com.company.community.domain.Post;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostListResponse {

    private Long id;
    private String title;
    private int viewCount;
    private int commentCount;
    private LocalDateTime createdAt;

    // ★ 목록에서도 작성자 노출 금지
    public static PostListResponse of(Post post, long commentCount) {
        return new PostListResponse(
                post.getId(),
                post.getTitle(),
                post.getViewCount(),
                (int) commentCount,
                post.getCreatedAt()
        );
    }
}
