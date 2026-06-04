package com.company.domain.comment.service;

import com.company.domain.comment.controller.dto.CommentCreateRequest;
import com.company.domain.comment.controller.dto.CommentResponse;
import com.company.domain.comment.entity.Comment;
import com.company.domain.comment.repository.CommentLikeRepository;
import com.company.domain.comment.repository.CommentRepository;
import com.company.domain.notification.service.NotificationService;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// [FEATURE:op-alias] CommentService 글 단위 익명 별칭(글쓴이/익명N) 단위 테스트
@ExtendWith(MockitoExtension.class)
class CommentServiceAliasTest {

    @Mock private CommentRepository commentRepository;
    @Mock private CommentLikeRepository commentLikeRepository; // [FEATURE:comment-likes]
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private CommentService commentService;

    @Test
    @DisplayName("getComments: 글쓴이는 '글쓴이', 나머지는 첫 등장 순 익명N, 동일 유저는 동일 별칭")
    void getComments_별칭_부여() {
        User op = user(1L, "옵닉");
        User u2 = user(2L, "닉2");
        User u3 = user(3L, "닉3");
        Post post = post(10L, op, PostCategory.QUESTION);
        // 시간순(asc): u2 → op → u3 → u2(재등장)
        List<Comment> ordered = List.of(
                comment(101L, post, u2),
                comment(102L, post, op),
                comment(103L, post, u3),
                comment(104L, post, u2)
        );
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findAllByPostIdOrderByCreatedAtAsc(10L)).willReturn(ordered);
        given(userRepository.findAllById(any())).willReturn(List.of(op, u2, u3));

        List<CommentResponse> res = commentService.getComments(10L, 999L);

        // 101(u2) → 첫 등장 익명1
        assertThat(res.get(0).getAlias()).isEqualTo("익명1");
        assertThat(res.get(0).isOp()).isFalse();
        // 102(op) → 글쓴이
        assertThat(res.get(1).getAlias()).isEqualTo("글쓴이");
        assertThat(res.get(1).isOp()).isTrue();
        // 103(u3) → 두 번째로 등장한 타인 익명2
        assertThat(res.get(2).getAlias()).isEqualTo("익명2");
        assertThat(res.get(2).isOp()).isFalse();
        // 104(u2 재등장) → 동일 유저는 동일 별칭(익명1)
        assertThat(res.get(3).getAlias()).isEqualTo("익명1");
    }

    @Test
    @DisplayName("getComments: 존재하지 않는 글이면 NoSuchElementException")
    void getComments_없는글_예외() {
        given(postRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.getComments(10L, 1L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("addComment: 글쓴이 본인 댓글이면 별칭 '글쓴이' + isAuthor=true (자기 글이라 알림 없음)")
    void addComment_글쓴이() {
        User op = user(1L, "옵닉");
        Post post = post(10L, op, PostCategory.FREE);
        Comment saved = comment(200L, post, op);
        CommentCreateRequest req = mock(CommentCreateRequest.class);
        given(req.getContent()).willReturn("내용");
        given(req.getParentId()).willReturn(null); // 최상위 댓글(답글 아님) — Mockito Long 기본값 0L 회피
        given(userRepository.findById(1L)).willReturn(Optional.of(op));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.save(any(Comment.class))).willReturn(saved);
        given(commentRepository.findAllByPostIdOrderByCreatedAtAsc(10L)).willReturn(List.of(saved));

        CommentResponse res = commentService.addComment(1L, 10L, req);

        assertThat(res.getAlias()).isEqualTo("글쓴이");
        assertThat(res.isOp()).isTrue();
        assertThat(res.isMine()).isTrue();
        verify(notificationService, never()).notifyComment(any(), anyLong(), any());
    }

    @Test
    @DisplayName("addComment: 타인의 첫 댓글이면 별칭 '익명1' + isAuthor=false (글쓴이에게 알림)")
    void addComment_타인_첫댓글() {
        User op = user(1L, "옵닉");
        User u2 = user(2L, "닉2");
        Post post = post(10L, op, PostCategory.FREE);
        Comment saved = comment(200L, post, u2);
        CommentCreateRequest req = mock(CommentCreateRequest.class);
        given(req.getContent()).willReturn("내용");
        given(req.getParentId()).willReturn(null); // 최상위 댓글(답글 아님) — Mockito Long 기본값 0L 회피
        given(userRepository.findById(2L)).willReturn(Optional.of(u2));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.save(any(Comment.class))).willReturn(saved);
        given(commentRepository.findAllByPostIdOrderByCreatedAtAsc(10L)).willReturn(List.of(saved));

        CommentResponse res = commentService.addComment(2L, 10L, req);

        assertThat(res.getAlias()).isEqualTo("익명1");
        assertThat(res.isOp()).isFalse();
        verify(notificationService).notifyComment(any(), anyLong(), any());
    }

    // --- helpers ---

    private User user(Long id, String nickname) {
        User u = User.builder()
                .mmUserId("mm-" + id).nickname(nickname)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(u, id);
        return u;
    }

    private Post post(Long id, User author, PostCategory category) {
        Post p = Post.builder().title("t").content("c").category(category).author(author).build();
        setId(p, id);
        return p;
    }

    private Comment comment(Long id, Post post, User author) {
        Comment c = Comment.builder().content("x").post(post).author(author).build();
        setId(c, id);
        return c;
    }

    private void setId(Object obj, Long id) {
        try {
            var f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
// [/FEATURE:op-alias]
