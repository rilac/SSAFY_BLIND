package com.company.community.service;

import com.company.community.domain.*;
import com.company.community.dto.PostCreateRequest;
import com.company.community.dto.PostResponse;
import com.company.community.exception.ForbiddenException;
import com.company.community.repository.BookmarkRepository;
import com.company.community.repository.CommentRepository;
import com.company.community.repository.NotificationRepository;
import com.company.community.repository.PostLikeRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.PostViewRepository;
import com.company.community.repository.ReportRepository;
import com.company.community.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
    private ReportRepository reportRepository; // C-NEW-1

    @Mock
    private NotificationRepository notificationRepository; // C-NEW-1

    @Mock
    private PostViewRepository postViewRepository; // M-NEW-5

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
    @DisplayName("작성자 본인 조회는 조회수가 증가하지 않는다 (M-NEW-5)")
    void test_작성자_본인조회_조회수_미증가() {
        // Arrange — author(ID=1)가 본인 글(author=1) 조회
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postLikeRepository.existsByPostIdAndUserId(10L, 1L)).willReturn(false);
        given(postLikeRepository.countByPostId(10L)).willReturn(0L);

        // Act
        PostResponse response = postService.getPost(10L, 1L, UserRole.USER);

        // Assert — 본인 조회는 이력 기록도, 조회수 증가도 없다
        verify(postViewRepository, never()).save(any());
        verify(postRepository, never()).incrementViewCount(anyLong());
        assertThat(response.getTitle()).isEqualTo("테스트 제목");
    }

    @Test
    @DisplayName("타인의 최초 조회는 이력 기록 + 조회수 증가 (M-NEW-5)")
    void test_타인_최초조회_조회수_증가() {
        // Arrange — otherUser(ID=2)가 author(ID=1)의 글 최초 조회
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postViewRepository.findByPostIdAndUserId(10L, 2L)).willReturn(Optional.empty());
        given(userRepository.findById(2L)).willReturn(Optional.of(otherUser));
        given(postLikeRepository.existsByPostIdAndUserId(10L, 2L)).willReturn(false);
        given(postLikeRepository.countByPostId(10L)).willReturn(0L);

        // Act
        postService.getPost(10L, 2L, UserRole.USER);

        // Assert
        verify(postViewRepository).save(any(PostView.class));
        verify(postRepository).incrementViewCount(10L);
    }

    @Test
    @DisplayName("타인의 24h 내 재조회는 조회수가 증가하지 않는다 (M-NEW-5)")
    void test_타인_24h내_재조회_미증가() {
        // Arrange — 1시간 전 조회 이력이 있는 상태
        PostView recent = PostView.builder().post(post).user(otherUser)
                .viewedAt(LocalDateTime.now().minusHours(1)).build();
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postViewRepository.findByPostIdAndUserId(10L, 2L)).willReturn(Optional.of(recent));
        given(postLikeRepository.existsByPostIdAndUserId(10L, 2L)).willReturn(false);
        given(postLikeRepository.countByPostId(10L)).willReturn(0L);

        // Act
        postService.getPost(10L, 2L, UserRole.USER);

        // Assert — 24h 내 재조회는 집계되지 않는다
        verify(postRepository, never()).incrementViewCount(anyLong());
    }

    @Test
    @DisplayName("본인 게시글 삭제 시 자식(신고/좋아요/북마크/알림) 정리 후 삭제된다")
    void test_본인_게시글_삭제_성공() {
        // Arrange
        given(postRepository.findById(10L)).willReturn(Optional.of(post));

        // Act — 예외 없이 실행되어야 함
        postService.deletePost(1L, 10L, UserRole.USER);

        // Assert — C-NEW-1: 자식 정리가 글 삭제보다 먼저 일어나야 FK 위반(500)을 피한다
        InOrder inOrder = inOrder(reportRepository, postLikeRepository,
                bookmarkRepository, postViewRepository, notificationRepository, postRepository);
        inOrder.verify(reportRepository).deleteByPostId(10L);
        inOrder.verify(postLikeRepository).deleteByPostId(10L);
        inOrder.verify(bookmarkRepository).deleteByPostId(10L);
        inOrder.verify(postViewRepository).deleteByPostId(10L);
        inOrder.verify(notificationRepository).deleteByPostId(10L);
        inOrder.verify(postRepository).delete(post);
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
