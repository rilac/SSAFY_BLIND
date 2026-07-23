package com.company.domain.post.service;

import com.company.domain.admin.service.AdminAuditService;
import com.company.domain.comment.repository.CommentLikeRepository;
import com.company.domain.comment.repository.CommentRepository;
import com.company.domain.notification.repository.NotificationRepository;
import com.company.domain.notification.service.NotificationService;
import com.company.domain.poll.repository.PollOptionRepository;
import com.company.domain.poll.repository.PollVoteRepository;
import com.company.domain.poll.service.PollService;
import com.company.domain.post.controller.dto.PostResponse;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.entity.Report;
import com.company.domain.post.entity.ReportReason;
import com.company.domain.post.repository.BookmarkRepository;
import com.company.domain.post.repository.PostLikeRepository;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.post.repository.PostViewRepository;
import com.company.domain.post.repository.ReportArchiveRepository;
import com.company.domain.post.repository.ReportRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// 2026-07 운영 장애 회귀 방지 — insertIgnore의 @Modifying(clearAutomatically=true)가 영속성 컨텍스트를
// 비워, 이미 로드한 Post가 detach되고 author LAZY 프록시가 LazyInitializationException으로 터졌다
// (타인 글 첫 조회 = INSERT 경로만 500, 본인 글·24h 내 재조회·touch 경로는 정상이라 발견이 늦었다).
// 단위 테스트(Mockito)는 영속성 컨텍스트 의미론을 재현하지 못하므로 H2(MySQL 모드) + 실제 세션으로 검증한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:viewregdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", // M-NEW-7: 베이스라인은 MySQL 전용 — H2 테스트에선 Flyway 미실행
})
class PostViewInsertRegressionTest {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private CommentRepository commentRepository;
    @Autowired private CommentLikeRepository commentLikeRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private ReportRepository reportRepository;
    @Autowired private ReportArchiveRepository reportArchiveRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PostViewRepository postViewRepository;
    @Autowired private PollOptionRepository pollOptionRepository;
    @Autowired private PollVoteRepository pollVoteRepository;
    @Autowired private TestEntityManager em;

    private PostService postService;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        PollService pollService = new PollService(
                pollOptionRepository, pollVoteRepository, postRepository, userRepository);
        postService = new PostService(
                postRepository, userRepository, postLikeRepository, commentRepository, commentLikeRepository,
                bookmarkRepository, reportRepository, reportArchiveRepository, notificationRepository,
                postViewRepository, Mockito.mock(NotificationService.class), pollService,
                Mockito.mock(AdminAuditService.class));
        reportService = new ReportService(reportRepository, postRepository, userRepository);
    }

    private User newUser(String mmUserId) {
        return userRepository.save(User.builder()
                .mmUserId(mmUserId).nickname("닉-" + mmUserId).cohort("16기").campus("서울")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build());
    }

    @Test
    @DisplayName("타인 글 첫 조회(INSERT 경로)도 상세 응답이 성공하고 작성자 정보·조회수가 채워진다")
    void test_타인_첫_조회_성공() {
        User author = newUser("author16");
        User viewer = newUser("viewer15");
        Post post = postRepository.save(Post.builder()
                .title("16기 첫 글").content("내용").category(PostCategory.FREE).author(author).build());
        em.flush();
        em.clear();

        // 장애 재현 조건: 조회자가 작성자가 아니고, 이 글을 처음 본다 → insertIgnore 실행 경로.
        // clearAutomatically=true였을 때 여기서 author 프록시가 LazyInitializationException으로 터졌다.
        PostResponse res = postService.getPost(post.getId(), viewer.getId(), UserRole.USER);

        assertThat(res.getAuthor().getNickname()).isEqualTo("닉-author16");
        assertThat(res.getAuthor().getCohort()).isEqualTo("16기");
        assertThat(res.getViewCount()).isEqualTo(1);

        // 조회 이력이 실제로 남았는지(롤백되지 않았는지) 확인
        em.flush();
        assertThat(postViewRepository.findByPostIdAndUserId(post.getId(), viewer.getId())).isPresent();
    }

    @Test
    @DisplayName("신고 임계값 도달 시 자동 숨김이 DB에 반영된다(detach로 dirty checking이 유실되지 않는다)")
    void test_신고_임계값_자동_숨김_반영() {
        User author = newUser("author");
        Post post = postRepository.save(Post.builder()
                .title("신고 대상").content("내용").category(PostCategory.FREE).author(author).build());
        // 서로 다른 신고자 4명 선적재 — 5번째 신고가 임계값(5)을 채운다.
        for (int i = 1; i <= 4; i++) {
            reportRepository.save(Report.builder()
                    .post(post).reporter(newUser("reporter" + i)).reason(ReportReason.SPAM).build());
        }
        User fifth = newUser("reporter5");
        em.flush();
        em.clear();

        // insertIgnore(신고 삽입) 직후 post.hide()가 dirty checking으로 반영돼야 한다.
        // clearAutomatically=true였을 때 post가 detach돼 hide()가 조용히 유실됐다.
        reportService.report(fifth.getId(), post.getId(), ReportReason.SPAM, null, UserRole.USER);
        em.flush();
        em.clear();

        assertThat(postRepository.findById(post.getId()).orElseThrow().isHidden()).isTrue();
    }
}
