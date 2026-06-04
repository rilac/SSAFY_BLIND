package com.company.domain.post.entity;

// 게시글 카테고리 — 고정 집합. 데모 사이드바/배지와 1:1 대응.
public enum PostCategory {
    FREE("자유게시판"),
    JOB("취준/취업"),
    QUESTION("질문"),
    // [FEATURE:food-board] 맛집 공유 게시판 — EnumType.STRING 저장이라 DB 마이그레이션 불필요.
    FOOD("맛집 공유");
    // [/FEATURE:food-board]

    private final String label;

    PostCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
