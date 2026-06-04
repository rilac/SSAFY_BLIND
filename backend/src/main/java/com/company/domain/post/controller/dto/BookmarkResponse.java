package com.company.domain.post.controller.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// 스크랩 토글 응답 (PostLikeResponse 패턴)
// boolean bookmarked → Lombok isBookmarked() getter → Jackson "bookmarked"로 직렬화
@Getter
@AllArgsConstructor
public class BookmarkResponse {

    private boolean bookmarked;
}
