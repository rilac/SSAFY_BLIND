package com.company.domain.post.service;

import com.company.domain.post.controller.dto.BookmarkResponse;
import com.company.domain.post.entity.Bookmark;
import com.company.domain.post.entity.Post;
import com.company.domain.post.repository.BookmarkRepository;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

    @Mock private BookmarkRepository bookmarkRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private BookmarkService bookmarkService;

    private User user;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder().mmUserId("mm").status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(user, 1L);
        post = Post.builder().title("제목").content("내용").author(user).build();
        setId(post, 10L);
    }

    @Test
    @DisplayName("스크랩이 없으면 추가하고 bookmarked=true를 반환한다")
    void test_스크랩_추가() {
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(bookmarkRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.empty());
        given(userRepository.existsById(1L)).willReturn(true);

        BookmarkResponse res = bookmarkService.toggle(1L, 10L, UserRole.USER);

        assertThat(res.isBookmarked()).isTrue();
        // save() → 멱등 INSERT로 전환(동시 요청 시 500 방지)
        verify(bookmarkRepository).insertIgnore(eq(10L), eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("이미 스크랩이면 삭제하고 bookmarked=false를 반환한다")
    void test_스크랩_취소() {
        Bookmark existing = Bookmark.builder().post(post).user(user).build();
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(bookmarkRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.of(existing));

        BookmarkResponse res = bookmarkService.toggle(1L, 10L, UserRole.USER);

        assertThat(res.isBookmarked()).isFalse();
        verify(bookmarkRepository).delete(existing);
        verify(bookmarkRepository, never()).insertIgnore(any(), any(), any());
    }

    private void setId(Object obj, Long id) {
        try {
            var field = obj.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
