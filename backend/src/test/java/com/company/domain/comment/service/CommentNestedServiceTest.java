package com.company.domain.comment.service;

import com.company.domain.comment.controller.dto.CommentCreateRequest;
import com.company.domain.comment.entity.Comment;
import com.company.domain.comment.repository.CommentRepository;
import com.company.domain.notification.service.NotificationService;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

// [FEATURE:nested-comments] 대댓글(1-depth) — 답글 생성/검증/알림/삭제 정리/채택 금지 단위 테스트
@ExtendWith(MockitoExtension.class)
class CommentNestedServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private CommentService commentService;

    @Test
    @DisplayName("답글 작성: parentId가 저장되고, 부모 댓글 작성자에게 알림(글 작성자 알림은 안 감)")
    void addReply_정상() {
        User postAuthor = user(1L);
        User parentAuthor = user(3L);
        User replier = user(2L);
        Post post = post(10L, postAuthor, PostCategory.FREE);
        Comment parent = comment(100L, post, parentAuthor, null);
        Comment savedReply = comment(200L, post, replier, 100L);

        CommentCreateRequest req = mock(CommentCreateRequest.class);
        given(req.getContent()).willReturn("답글");
        given(req.getParentId()).willReturn(100L);
        given(userRepository.findById(2L)).willReturn(Optional.of(replier));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findById(100L)).willReturn(Optional.of(parent));
        given(commentRepository.save(any(Comment.class))).willReturn(savedReply);
        given(commentRepository.findAllByPostIdOrderByCreatedAtAsc(10L)).willReturn(List.of(parent, savedReply));

        commentService.addComment(2L, 10L, req);

        ArgumentCaptor<Comment> cap = ArgumentCaptor.forClass(Comment.class);
        verify(commentRepository).save(cap.capture());
        assertThat(cap.getValue().getParentId()).isEqualTo(100L);
        // 답글 → 부모 댓글 작성자에게 알림, 글 작성자 댓글 알림은 발생하지 않음
        verify(notificationService).notifyReply(parentAuthor, 10L);
        verify(notificationService, never()).notifyComment(any(), anyLong(), any());
    }

    @Test
    @DisplayName("답글에 다시 답글을 달면 InvalidStateException (1-depth)")
    void addReply_2depth_거부() {
        Post post = post(10L, user(1L), PostCategory.FREE);
        Comment parentIsReply = comment(100L, post, user(3L), 50L); // 이미 답글(부모 있음)

        CommentCreateRequest req = mock(CommentCreateRequest.class);
        given(req.getParentId()).willReturn(100L);
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findById(100L)).willReturn(Optional.of(parentIsReply));

        assertThatThrownBy(() -> commentService.addComment(2L, 10L, req))
                .isInstanceOf(InvalidStateException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 글의 댓글에 답글을 달면 NoSuchElementException")
    void addReply_다른글부모_거부() {
        Post post = post(10L, user(1L), PostCategory.FREE);
        Post other = post(99L, user(1L), PostCategory.FREE);
        Comment parentOnOther = comment(100L, other, user(3L), null);

        CommentCreateRequest req = mock(CommentCreateRequest.class);
        given(req.getParentId()).willReturn(100L);
        given(userRepository.findById(2L)).willReturn(Optional.of(user(2L)));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findById(100L)).willReturn(Optional.of(parentOnOther));

        assertThatThrownBy(() -> commentService.addComment(2L, 10L, req))
                .isInstanceOf(NoSuchElementException.class);
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("댓글 삭제 시 그 답글들도 함께 정리(deleteByParentId)")
    void deleteComment_답글_정리() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.FREE);
        Comment parent = comment(100L, post, author, null);
        given(commentRepository.findById(100L)).willReturn(Optional.of(parent));

        commentService.deleteComment(1L, UserRole.USER, 10L, 100L);

        verify(commentRepository).deleteByParentId(100L);
        verify(commentRepository).delete(parent);
    }

    @Test
    @DisplayName("답글(대댓글)은 채택할 수 없다 — InvalidStateException")
    void acceptReply_거부() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.QUESTION);
        Comment reply = comment(100L, post, user(2L), 50L); // 답글

        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findById(100L)).willReturn(Optional.of(reply));

        assertThatThrownBy(() -> commentService.toggleAcceptAnswer(1L, 10L, 100L))
                .isInstanceOf(InvalidStateException.class);
        assertThat(post.getAcceptedCommentId()).isNull();
    }

    // --- helpers ---

    private User user(Long id) {
        User u = User.builder().mmUserId("mm-" + id).nickname("닉" + id)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(u, id);
        return u;
    }

    private Post post(Long id, User author, PostCategory category) {
        Post p = Post.builder().title("t").content("c").category(category).author(author).build();
        setId(p, id);
        return p;
    }

    private Comment comment(Long id, Post post, User author, Long parentId) {
        Comment c = Comment.builder().content("x").post(post).author(author).parentId(parentId).build();
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
// [/FEATURE:nested-comments]
