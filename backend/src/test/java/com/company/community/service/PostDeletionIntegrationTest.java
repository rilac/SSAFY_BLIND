package com.company.community.service;

import com.company.community.domain.Bookmark;
import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import com.company.community.domain.PostLike;
import com.company.community.domain.PostView;
import com.company.community.domain.Report;
import com.company.community.domain.ReportReason;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
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
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// C-NEW-1 회귀 방지 — 신고/좋아요/북마크/조회이력(모두 post_id FK)이 달린 글도
// 실제 FK 제약 하에서 삭제가 성공해야 한다. 단위 테스트(Mockito)는 FK를 강제하지 않으므로
// H2(MySQL 호환 모드)로 실제 DDL/FK를 적용해 검증한다. PostService는 리포지토리만 주입해 수동 구성.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:deltestdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", // M-NEW-7: 베이스라인은 MySQL 전용 — H2 테스트에선 Flyway 미실행
})
class PostDeletionIntegrationTest {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PostViewRepository postViewRepository;
    @Autowired private TestEntityManager em;

    private PostService postService;

    @BeforeEach
    void setUp() {
        // 좋아요 알림(NotificationService)은 삭제 경로와 무관 → 목으로 대체.
        postService = new PostService(
                postRepository, userRepository, postLikeRepository, commentRepository,
                bookmarkRepository, reportRepository, notificationRepository, postViewRepository,
                Mockito.mock(NotificationService.class));
    }

    @Test
    @DisplayName("신고/좋아요/북마크/조회이력이 달린 글도 FK 위반 없이 삭제된다 (C-NEW-1 회귀)")
    void test_자식_달린_글_삭제_성공() {
        User author = userRepository.save(User.builder()
                .mmUserId("author").status(UserStatus.ACTIVE).role(UserRole.USER).build());
        User other = userRepository.save(User.builder()
                .mmUserId("other").status(UserStatus.ACTIVE).role(UserRole.USER).build());
        Post post = postRepository.save(Post.builder()
                .title("신고된 글").content("내용").category(PostCategory.FREE).author(author).build());

        // post_id를 FK로 참조하는 자식들을 모두 생성
        postLikeRepository.save(PostLike.builder().post(post).user(other).build());
        bookmarkRepository.save(Bookmark.builder().post(post).user(other).build());
        reportRepository.save(Report.builder().post(post).reporter(other).reason(ReportReason.SPAM).build());
        postViewRepository.save(PostView.builder().post(post).user(other)
                .viewedAt(LocalDateTime.now()).build());

        em.flush();
        em.clear();

        Long postId = post.getId();

        // 정리 없이 postRepository.delete만 했다면 여기서 FK 위반 → 예외. 예외가 없어야 한다.
        assertThatCode(() -> {
            postService.deletePost(author.getId(), postId, UserRole.USER);
            em.flush();
        }).doesNotThrowAnyException();

        em.clear();

        // 글과 자식들이 모두 정리됐는지 확인
        assertThat(postRepository.findById(postId)).isEmpty();
        assertThat(postLikeRepository.existsByPostIdAndUserId(postId, other.getId())).isFalse();
        assertThat(bookmarkRepository.existsByPostIdAndUserId(postId, other.getId())).isFalse();
        assertThat(reportRepository.countByPostId(postId)).isZero();
        assertThat(postViewRepository.findByPostIdAndUserId(postId, other.getId())).isEmpty();
    }
}
