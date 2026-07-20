package com.company.domain.post.service;

import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.repository.BookmarkRepository;
import com.company.domain.post.repository.PostLikeRepository;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.post.repository.PostViewRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 멱등 삽입 회귀 방지 — 같은 유니크 키로 두 번 삽입해도 예외 없이 0행이 반환되어야 한다.
 *
 * <p>기존 코드는 save() + catch(DataIntegrityViolationException) 였는데, IDENTITY 전략이라 save()가
 * 즉시 INSERT를 날리고 제약 위반 시 Hibernate가 트랜잭션을 rollback-only로 마킹했다. catch로 삼켜도
 * 커밋 시점에 UnexpectedRollbackException이 터져 사용자는 500을 받았다(좋아요 더블클릭·두 탭 동시 조회).
 * INSERT IGNORE는 애초에 예외를 만들지 않으므로 그 경로가 구조적으로 사라진다.
 *
 * <p>Mockito 단위 테스트로는 유니크 제약이 강제되지 않아 증명할 수 없으므로 H2(MySQL 모드)로 실제 DDL을 건다.
 * INSERT IGNORE 문법도 MODE=MySQL에서만 파싱되므로 이 설정이 필수다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:idemtestdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
})
class ConcurrentIdempotentInsertTest {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private BookmarkRepository bookmarkRepository;
    @Autowired private PostViewRepository postViewRepository;
    @Autowired private TestEntityManager em;

    private record Fixture(Long postId, Long userId) {}

    private Fixture fixture(String tag) {
        User author = userRepository.save(User.builder()
                .mmUserId("author-" + tag).status(UserStatus.ACTIVE).role(UserRole.USER).build());
        User actor = userRepository.save(User.builder()
                .mmUserId("actor-" + tag).status(UserStatus.ACTIVE).role(UserRole.USER).build());
        Post post = postRepository.save(Post.builder()
                .title("글").content("내용").category(PostCategory.FREE).author(author).build());
        em.flush();
        return new Fixture(post.getId(), actor.getId());
    }

    @Test
    @DisplayName("반응 중복 삽입: 예외 없이 1행 → 0행 (더블클릭이 500이 되지 않는다)")
    void test_반응_멱등() {
        Fixture f = fixture("like");
        LocalDateTime now = LocalDateTime.now();

        assertThatCode(() -> {
            assertThat(postLikeRepository.insertIgnore(f.postId(), f.userId(), "LIKE", now)).isEqualTo(1);
            assertThat(postLikeRepository.insertIgnore(f.postId(), f.userId(), "LIKE", now)).isZero();
            em.flush(); // 여기서 UnexpectedRollbackException이 나면 회귀
        }).doesNotThrowAnyException();

        assertThat(postLikeRepository.findByPostIdAndUserId(f.postId(), f.userId())).isPresent();
    }

    @Test
    @DisplayName("스크랩 중복 삽입: 예외 없이 1행 → 0행")
    void test_스크랩_멱등() {
        Fixture f = fixture("bm");
        LocalDateTime now = LocalDateTime.now();

        assertThatCode(() -> {
            assertThat(bookmarkRepository.insertIgnore(f.postId(), f.userId(), now)).isEqualTo(1);
            assertThat(bookmarkRepository.insertIgnore(f.postId(), f.userId(), now)).isZero();
            em.flush();
        }).doesNotThrowAnyException();

        assertThat(bookmarkRepository.existsByPostIdAndUserId(f.postId(), f.userId())).isTrue();
    }

    @Test
    @DisplayName("조회 이력 중복 삽입: 예외 없이 1행 → 0행 (두 탭 동시 조회가 500이 되지 않는다)")
    void test_조회이력_멱등() {
        Fixture f = fixture("view");
        LocalDateTime now = LocalDateTime.now();

        assertThatCode(() -> {
            assertThat(postViewRepository.insertIgnore(f.postId(), f.userId(), now)).isEqualTo(1);
            assertThat(postViewRepository.insertIgnore(f.postId(), f.userId(), now)).isZero();
            em.flush();
        }).doesNotThrowAnyException();

        assertThat(postViewRepository.findByPostIdAndUserId(f.postId(), f.userId())).isPresent();
    }
}
