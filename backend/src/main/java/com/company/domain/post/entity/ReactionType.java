package com.company.domain.post.entity;

// [FEATURE:reactions] 게시글 반응 종류 — 1인 1반응(단일 선택). LIKE는 기존 좋아요와 호환(기존 행은 LIKE로 마이그레이션).
public enum ReactionType {
    LIKE,        // 좋아요
    HELPFUL,     // 도움돼요
    INFORMATIVE, // 정보
    EMPATHY      // 공감
}
