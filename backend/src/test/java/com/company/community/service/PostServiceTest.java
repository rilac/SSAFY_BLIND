package com.company.community.service;

import com.company.community.domain.*;
import com.company.community.dto.PostCreateRequest;
import com.company.community.dto.PostResponse;
import com.company.community.exception.ForbiddenException;
import com.company.community.repository.BookmarkRepository;
import com.company.community.repository.CommentRepository;
import com.company.community.repository.PostLikeRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

// (#6) PostService 단위 테스트
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostLikeRepository postLikeRepository; // (#4)

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private BookmarkRepository bookmarkRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PostService postService;

    private User author;
    private User otherUser;
    private Post post;

    @BeforeEach
    void setUp() {
        // 게시글 작성자 (ID=1)
        author = User.builder()
                .mmUserId("author-mm")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        // ID 직접 설정 불가(PROTECTED 생성자) → 리플렉션 사용
        setId(author, 1L);

        // 타인 유저 (ID=2)
        otherUser = User.builder()
                .mmUserId("other-mm")
                .status(UserStatus.ACTIVE)
                .role(UserRole.USER)
                .build();
        setId(otherUser, 2L);

        // 테스트용 게시글
        post = Post.builder()
                .title("테스트 제목")
                .content("테스트 내용")
                .author(author)
                .build();
        setId(post, 10L);
    }

    // 리플렉션으로 private id 필드 설정
    private void setId(Object obj, Long id) {
        try {
            var field = obj.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("게시글 작성 시 PostResponse가 정상 반환된다")
    void test_게시글_작성_정상동작() {
        // Arrange
        PostCreateRequest request = new PostCreateRequest();
        setField(request, "title", "새 게시글");
        setField(request, "content", "새 내용");

        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.save(any(Post.class))).willReturn(post);

        // Act
        PostResponse response = postService.createPost(1L, request);

        // Assert
        assertThat(response.getTitle()).isEqualTo("테스트 제목");
        assertThat(response.getContent()).isEqualTo("테스트 내용");
        assertThat(response.isMine()).isTrue();
    }

    @Test
    @DisplayName("게시글 조회 시 incrementViewCount가 호출된다")
    void test_게시글_조회시_조회수_증가() {
        // Arrange
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(false);
        given(postLikeRepository.countByPostId(10L)).willReturn(0L);

        // Act
        PostResponse response = postService.getPost(10L, 1L, UserRole.USER);

        // Assert — incrementViewCount() 호출 검증
        verify(postRepository).incrementViewCount(10L);
        assertThat(response.getTitle()).isEqualTo("테스트 제목");
    }

    @Test
    @DisplayName("본인 게시글 삭제 시 정상 삭제된다")
    void test_본인_게시글_삭제_성공() {
        // Arrange
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        // Act — 예외 없이 실행되어야 함
        postService.deletePost(1L, 10L, UserRole.USER);

        // Assert
        verify(postRepository).delete(post);
    }

    @Test
    @DisplayName("타인 게시글 삭제 시 ForbiddenException이 발생한다")
    void test_타인_게시글_삭제시_ForbiddenException() {
        // Arrange — otherUser(ID=2)가 author(ID=1)의 글 삭제 시도
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        // Assert
        assertThatThrownBy(() -> postService.deletePost(2L, 10L, UserRole.USER))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("ADMIN은 타인 게시글도 삭제할 수 있다")
    void test_ADMIN은_타인_게시글_삭제_가능() {
        // Arrange — ADMIN 권한의 otherUser(ID=2)가 author(ID=1)의 글 삭제
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        // Act — ADMIN이므로 예외 없이 실행되어야 함
        postService.deletePost(2L, 10L, UserRole.ADMIN);

        // Assert
        verify(postRepository).delete(post);
    }

    // 리플렉션으로 DTO 필드 설정
    private void setField(Object obj, String fieldName, Object value) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
