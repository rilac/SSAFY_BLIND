package com.company.domain.post.entity;

import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.global.exception.InvalidStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// [FEATURE:hidden-author-visibility] 숨김 글 가시성/쓰기 규칙 — 상세·댓글·반응·스크랩·투표·신고가 공유하는 단일 기준.
class PostVisibilityTest {

    private static final Long AUTHOR_ID = 1L;
    private static final Long OTHER_ID = 2L;

    private Post post(boolean hidden) {
        User author = User.builder().id(AUTHOR_ID).mmUserId("a").build();
        Post p = Post.builder().id(10L).title("t").content("c").author(author).build();
        if (hidden) {
            p.hide();
        }
        return p;
    }

    @Test
    @DisplayName("공개 글은 누구나 볼 수 있다(비로그인 포함)")
    void test_공개글_전원_열람() {
        Post p = post(false);

        assertThat(p.isVisibleTo(UserRole.USER, OTHER_ID)).isTrue();
        assertThat(p.isVisibleTo(UserRole.ADMIN, OTHER_ID)).isTrue();
        assertThat(p.isVisibleTo(UserRole.USER, null)).isTrue(); // 비로그인
    }

    @Test
    @DisplayName("숨김 글은 작성자 본인과 관리자만 볼 수 있다")
    void test_숨김글_작성자와_관리자만() {
        Post p = post(true);

        assertThat(p.isVisibleTo(UserRole.USER, AUTHOR_ID)).isTrue();   // 작성자 본인
        assertThat(p.isVisibleTo(UserRole.ADMIN, OTHER_ID)).isTrue();   // 관리자
        assertThat(p.isVisibleTo(UserRole.USER, OTHER_ID)).isFalse();   // 제3자
    }

    @Test
    @DisplayName("숨김 글은 비로그인에게 절대 보이지 않는다(작성자일 수 없으므로 fail-closed)")
    void test_숨김글_비로그인_차단() {
        Post p = post(true);

        assertThat(p.isVisibleTo(UserRole.USER, null)).isFalse();
        assertThatThrownBy(() -> p.assertVisibleTo(UserRole.USER, null))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("볼 수 없으면 403이 아니라 404 — 숨김 사실 자체를 노출하지 않는다")
    void test_제3자는_404() {
        assertThatThrownBy(() -> post(true).assertVisibleTo(UserRole.USER, OTHER_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("존재하지 않는 게시글");
    }

    @Test
    @DisplayName("숨김 글에는 작성자도 관리자도 쓰기 불가 — 검수 회피/유령 활동 방지")
    void test_숨김글_쓰기_전원차단() {
        Post p = post(true);

        // 작성자: 볼 수는 있지만(404 아님) 쓰기는 400
        assertThatThrownBy(() -> p.assertWritable(UserRole.USER, AUTHOR_ID))
                .isInstanceOf(InvalidStateException.class);
        // 관리자도 동일
        assertThatThrownBy(() -> p.assertWritable(UserRole.ADMIN, OTHER_ID))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    @DisplayName("제3자의 쓰기는 400이 아니라 404 — 400을 주면 글의 존재·모더레이션 상태가 샌다")
    void test_제3자_쓰기는_404가_먼저() {
        assertThatThrownBy(() -> post(true).assertWritable(UserRole.USER, OTHER_ID))
                .isInstanceOf(NoSuchElementException.class); // InvalidStateException이 아니어야 한다
    }

    @Test
    @DisplayName("공개 글은 쓰기가 자유롭다")
    void test_공개글_쓰기_허용() {
        Post p = post(false);

        assertThatCode(() -> p.assertWritable(UserRole.USER, OTHER_ID)).doesNotThrowAnyException();
        assertThatCode(() -> p.assertWritable(UserRole.USER, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("복원하면 다시 전원 열람·쓰기 가능")
    void test_복원_후_원복() {
        Post p = post(true);
        p.restore();

        assertThat(p.isVisibleTo(UserRole.USER, OTHER_ID)).isTrue();
        assertThatCode(() -> p.assertWritable(UserRole.USER, OTHER_ID)).doesNotThrowAnyException();
    }
}
