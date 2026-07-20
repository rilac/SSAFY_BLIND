package com.company.domain.comment;

import com.company.domain.comment.controller.dto.CommentCreateRequest;
import com.company.domain.comment.entity.Comment;

import jakarta.persistence.Column;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// 댓글 본문 길이 계약 — DTO @Size(max)와 엔티티 @Column(length)이 어긋나면 Bean Validation을 통과한 요청이
// INSERT 단계에서 데이터 잘림으로 실패하고 409로 응답된다(사용자에겐 원인 불명의 충돌). 그 불일치를 여기서 잡는다.
// H2 테스트 프로파일은 create-drop이라 엔티티 매핑대로 스키마를 새로 만들므로 마이그레이션 누락을 재현할 수 없다
// (V8 주석의 "H2 테스트는 validate OFF라 이 차이를 못 잡음"과 같은 한계). 그래서 DB 왕복 대신 계약 자체를 검증한다.
// MySQL 스키마와의 최종 일치는 기동 시 ddl-auto=validate가 담당한다.
class CommentContentLengthTest {

    // 이 값을 바꿀 때는 CommentCreateRequest.@Size · Comment.@Column(length) · Flyway 마이그레이션 ·
    // 프론트 PostDetailPage.COMMENT_MAX를 함께 바꿔야 한다.
    private static final int CONTENT_MAX = 1000;

    @Test
    @DisplayName("엔티티 @Column(length)가 DTO @Size(max)와 일치한다")
    void test_길이_계약_일치() throws NoSuchFieldException {
        Field content = Comment.class.getDeclaredField("content");
        assertThat(content.getAnnotation(Column.class).length()).isEqualTo(CONTENT_MAX);
    }

    @Test
    @DisplayName("상한 길이(1000자) 댓글은 검증을 통과한다")
    void test_상한_통과() {
        assertThat(validate("가".repeat(CONTENT_MAX))).isEmpty();
    }

    @Test
    @DisplayName("상한 초과(1001자) 댓글은 검증에서 걸러진다 — DB 잘림(409)까지 가지 않는다")
    void test_상한_초과_거부() {
        assertThat(validate("가".repeat(CONTENT_MAX + 1))).hasSize(1);
    }

    private Set<ConstraintViolation<CommentCreateRequest>> validate(String content) {
        CommentCreateRequest request = new CommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", content); // @Setter 없는 DTO — Jackson과 동일하게 필드 주입
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(request);
        }
    }
}
