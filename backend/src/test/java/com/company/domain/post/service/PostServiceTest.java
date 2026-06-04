package com.company.domain.post.service;

import com.company.domain.comment.repository.CommentRepository;
import com.company.domain.notification.repository.NotificationRepository;
import com.company.domain.notification.service.NotificationService;
import com.company.domain.poll.service.PollService;
import com.company.domain.post.controller.dto.PostCreateRequest;
import com.company.domain.post.controller.dto.PostListResponse;
import com.company.domain.post.controller.dto.PostResponse;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.entity.PostLike;
import com.company.domain.post.entity.PostView;
import com.company.domain.post.entity.ReactionType;
import com.company.domain.post.repository.BookmarkRepository;
import com.company.domain.post.repository.PostLikeRepository;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.post.repository.PostViewRepository;
import com.company.domain.post.repository.ReportRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.ForbiddenException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private PollService pollService; // [FEATURE:poll]

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

    // [FEATURE:cohort-campus-lounge] scope=campus/cohort는 현재 유저의 캠퍼스/기수를 repo 필터로 넘긴다
    @Test
    @DisplayName("scope=campus는 유저의 캠퍼스를 필터로, cohort는 null로 넘긴다")
    void test_scope_campus_유저캠퍼스_필터() {
        author = User.builder().mmUserId("a").cohort("10기").campus("서울")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(author, 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findFilteredLatest(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        postService.getAllPosts(0, 20, 1L, null, null, "latest", "campus", UserRole.USER);

        var cohortCap = org.mockito.ArgumentCaptor.forClass(String.class);
        var campusCap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(postRepository).findFilteredLatest(any(), any(), any(), any(),
                cohortCap.capture(), campusCap.capture(), anyBoolean(), any());
        assertThat(campusCap.getValue()).isEqualTo("서울");
        assertThat(cohortCap.getValue()).isNull();
    }

    @Test
    @DisplayName("scope=cohort는 유저의 기수를 필터로, campus는 null로 넘긴다")
    void test_scope_cohort_유저기수_필터() {
        author = User.builder().mmUserId("a").cohort("10기").campus("서울")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(author, 1L);
        given(userRepository.findById(1L)).willReturn(Optional.of(author));
        given(postRepository.findFilteredLatest(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        postService.getAllPosts(0, 20, 1L, null, null, "latest", "cohort", UserRole.USER);

        var cohortCap = org.mockito.ArgumentCaptor.forClass(String.class);
        var campusCap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(postRepository).findFilteredLatest(any(), any(), any(), any(),
                cohortCap.capture(), campusCap.capture(), anyBoolean(), any());
        assertThat(cohortCap.getValue()).isEqualTo("10기");
        assertThat(campusCap.getValue()).isNull();
    }

    @Test
    @DisplayName("scope=all은 유저를 로드하지 않고 cohort/campus 필터가 모두 null이다")
    void test_scope_all_라운지필터_없음() {
        given(postRepository.findFilteredLatest(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        postService.getAllPosts(0, 20, 1L, null, null, "latest", "all", UserRole.USER);

        verify(userRepository, never()).findById(anyLong());
        var cohortCap = org.mockito.ArgumentCaptor.forClass(String.class);
        var campusCap = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(postRepository).findFilteredLatest(any(), any(), any(), any(),
                cohortCap.capture(), campusCap.capture(), anyBoolean(), any());
        assertThat(cohortCap.getValue()).isNull();
        assertThat(campusCap.getValue()).isNull();
    }
    // [/FEATURE:cohort-campus-lounge]

    // [FEATURE:admin-moderation] 관리자 목록은 숨김 글 포함(includeHidden=true), 일반 유저는 제외(false).
    @Test
    @DisplayName("getAllPosts: role=ADMIN이면 includeHidden=true, USER면 false로 repo에 넘긴다")
    void test_관리자_숨김글_목록포함_플래그() {
        given(postRepository.findFilteredLatest(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .willReturn(org.springframework.data.domain.Page.empty());

        postService.getAllPosts(0, 20, 1L, null, null, "latest", "all", UserRole.ADMIN);
        postService.getAllPosts(0, 20, 1L, null, null, "latest", "all", UserRole.USER);

        verify(postRepository).findFilteredLatest(any(), any(), any(), any(), any(), any(), eq(true), any());
        verify(postRepository).findFilteredLatest(any(), any(), any(), any(), any(), any(), eq(false), any());
    }

    // [FEATURE:unread-new] 안 읽은 새 글(NEW)/읽음 표시 계산
    @Test
    @DisplayName("getAllPosts: 미열람+최근=NEW, 열람=읽음, 본인 글/오래된 글은 NEW 아님")
    void test_안읽은_새글_읽음_표시() {
        LocalDateTime now = LocalDateTime.now();
        Post recentUnread = postWith(11L, otherUser, now);            // 미열람+최근 → NEW
        Post recentRead = postWith(12L, otherUser, now);              // 열람 → 읽음
        Post mine = postWith(13L, author, now);                      // 본인 글 → NEW 아님
        Post oldUnread = postWith(14L, otherUser, now.minusDays(10)); // 오래됨 → NEW 아님

        given(postRepository.findFilteredLatest(any(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                .willReturn(new PageImpl<>(List.of(recentUnread, recentRead, mine, oldUnread)));
        given(commentRepository.countByPostIds(any())).willReturn(List.of());
        given(postLikeRepository.countByPostIds(any())).willReturn(List.of());
        given(postLikeRepository.findUserReactions(any(), anyLong())).willReturn(List.of()); // [FEATURE:reactions]
        given(bookmarkRepository.findBookmarkedPostIds(any(), anyLong())).willReturn(List.of());
        given(pollService.hasPollPostIds(any())).willReturn(Set.of());
        given(postViewRepository.findViewedPostIds(any(), anyLong())).willReturn(List.of(12L)); // recentRead만 열람
        given(userRepository.findAllById(any())).willReturn(List.of(author, otherUser));

        // currentUserId = author(1L)
        List<PostListResponse> content = postService
                .getAllPosts(0, 20, 1L, null, null, "latest", "all", UserRole.USER).getContent();

        assertThat(content.get(0).isNew()).isTrue();   // recentUnread
        assertThat(content.get(0).isRead()).isFalse();
        assertThat(content.get(1).isNew()).isFalse();  // recentRead
        assertThat(content.get(1).isRead()).isTrue();
        assertThat(content.get(2).isNew()).isFalse();  // mine(본인 글)
        assertThat(content.get(3).isNew()).isFalse();  // oldUnread(7일 초과)
    }

    private Post postWith(Long id, User postAuthor, LocalDateTime createdAt) {
        Post p = Post.builder().title("t").content("c").category(PostCategory.FREE)
                .author(postAuthor).createdAt(createdAt).build();
        setId(p, id);
        return p;
    }
    // [/FEATURE:unread-new]

    // [FEATURE:reactions] 반응 토글 — 신규(저장+알림)/같은 종류(취소)/다른 종류(변경)
    @Test
    @DisplayName("react: 처음 누르면 해당 종류로 저장 + 글 작성자에게 알림")
    void test_반응_신규() {
        given(userRepository.findById(2L)).willReturn(Optional.of(otherUser));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postLikeRepository.findByPostIdAndUserId(10L, 2L)).willReturn(Optional.empty());

        postService.react(2L, 10L, ReactionType.HELPFUL);

        ArgumentCaptor<PostLike> cap = ArgumentCaptor.forClass(PostLike.class);
        verify(postLikeRepository).save(cap.capture());
        assertThat(cap.getValue().getReactionType()).isEqualTo(ReactionType.HELPFUL);
        verify(notificationService).notifyReaction(any(), anyLong(), any());
    }

    @Test
    @DisplayName("react: 같은 종류를 다시 누르면 취소(delete)")
    void test_반응_취소() {
        PostLike existing = PostLike.builder().post(post).user(otherUser).reactionType(ReactionType.LIKE).build();
        given(userRepository.findById(2L)).willReturn(Optional.of(otherUser));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postLikeRepository.findByPostIdAndUserId(10L, 2L)).willReturn(Optional.of(existing));

        postService.react(2L, 10L, ReactionType.LIKE);

        verify(postLikeRepository).delete(existing);
        verify(postLikeRepository, never()).save(any());
    }

    @Test
    @DisplayName("react: 다른 종류를 누르면 변경(changeType, delete/save 없음)")
    void test_반응_변경() {
        PostLike existing = PostLike.builder().post(post).user(otherUser).reactionType(ReactionType.LIKE).build();
        given(userRepository.findById(2L)).willReturn(Optional.of(otherUser));
        given(postRepository.findById(10L)).willReturn(Optional.of(post));
        given(postLikeRepository.findByPostIdAndUserId(10L, 2L)).willReturn(Optional.of(existing));

        postService.react(2L, 10L, ReactionType.INFORMATIVE);

        assertThat(existing.getReactionType()).isEqualTo(ReactionType.INFORMATIVE);
        verify(postLikeRepository, never()).delete(any());
        verify(postLikeRepository, never()).save(any());
    }
    // [/FEATURE:reactions]

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
