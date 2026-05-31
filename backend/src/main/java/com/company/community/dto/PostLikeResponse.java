package com.company.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

// (#4) 좋아요 토글 응답 DTO
@Getter
@AllArgsConstructor
public class PostLikeResponse {

    // liked: 현재 좋아요 상태 (true=좋아요, false=취소)
    // boolean liked → Lombok이 isLiked() getter 생성 → Jackson이 "liked"로 직렬화
    private boolean liked;

    // 토글 후 최신 좋아요 수
    private long likeCount;
}
