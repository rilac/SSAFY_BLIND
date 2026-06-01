package com.company.community.service;

import com.company.community.domain.Comment;
import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import com.company.community.dto.AcceptAnswerResponse;
import com.company.community.exception.ForbiddenException;
import com.company.community.exception.InvalidStateException;
import com.company.community.repository.CommentRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// [FEATURE:qna-accept] CommentService 단위 테스트 — 답변 채택 토글 + 채택 댓글 삭제 정리
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private CommentService commentService;

    private User user(Long id) {
        User u = User.builder().mmUserId("mm-" + id).status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(u, id);
        return u;
    }

    private Post post(Long id, User author, PostCategory category) {
        Post p = Post.builder().title("t").content("c").category(category).author(author).build();
        setId(p, id);
        return p;
    }

    private Comment comment(Long id, Post post, User author) {
        Comment c = Comment.builder().content("answer").post(post).author(author).build();
        setId(c, id);
        return c;
    }

    @Test
    @DisplayName("QUESTION 글 작성자가 답변을 채택하면 acceptedCommentId가 설정된다")
    void test_accept_성공() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.QUESTION);
        Comment comment = comment(100L, post, user(2L));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        AcceptAnswerResponse res = commentService.toggleAcceptAnswer(1L, 10L, 100L);

        assertThat(post.getAcceptedCommentId()).isEqualTo(100L);
        assertThat(res.acceptedCommentId()).isEqualTo(100L);
        assertThat(res.solved()).isTrue();
    }

    @Test
    @DisplayName("이미 채택된 답변을 다시 요청하면 채택이 해제된다(토글)")
    void test_accept_토글_해제() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.QUESTION);
        post.acceptAnswer(100L); // 이미 채택된 상태
        Comment comment = comment(100L, post, user(2L));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        AcceptAnswerResponse res = commentService.toggleAcceptAnswer(1L, 10L, 100L);

        assertThat(post.getAcceptedCommentId()).isNull();
        assertThat(res.solved()).isFalse();
    }

    @Test
    @DisplayName("질문 작성자가 아니면 ForbiddenException")
    void test_accept_비작성자_거부() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.QUESTION);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> commentService.toggleAcceptAnswer(2L, 10L, 100L))
                .isInstanceOf(ForbiddenException.class);
        verify(commentRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("QUESTION 글이 아니면 InvalidStateException")
    void test_accept_비질문글_거부() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.FREE);
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        assertThatThrownBy(() -> commentService.toggleAcceptAnswer(1L, 10L, 100L))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    @DisplayName("다른 글의 댓글을 채택하려 하면 NoSuchElementException")
    void test_accept_다른글_댓글_거부() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.QUESTION);
        Post other = post(99L, author, PostCategory.QUESTION);
        Comment comment = comment(100L, other, user(2L)); // 다른 글(99)의 댓글
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.toggleAcceptAnswer(1L, 10L, 100L))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("채택된 답변이 삭제되면 글의 채택 상태가 해제된다")
    void test_채택답변_삭제시_정리() {
        User author = user(1L);
        Post post = post(10L, author, PostCategory.QUESTION);
        post.acceptAnswer(100L);
        Comment comment = comment(100L, post, author); // 작성자가 본인 댓글 삭제
        given(commentRepository.findById(100L)).willReturn(Optional.of(comment));

        commentService.deleteComment(1L, UserRole.USER, 10L, 100L);

        assertThat(post.getAcceptedCommentId()).isNull();
        verify(commentRepository).delete(comment);
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
// [/FEATURE:qna-accept]
