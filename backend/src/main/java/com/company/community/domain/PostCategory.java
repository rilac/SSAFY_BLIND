package com.company.community.domain;

// 게시글 카테고리 — 고정 집합. 데모 사이드바/배지와 1:1 대응.
public enum PostCategory {
    FREE("자유게시판"),
    JOB("취업/이직"),
    QUESTION("질문");

    private final String label;

    PostCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
