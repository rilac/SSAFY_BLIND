package com.company.domain.post.repository;

import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// [FEATURE:cohort-campus-lounge] 기수/캠퍼스 라운지 필터 — H2(MySQL 호환)로 실제 JPQL 실행 검증
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:loungedb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
})
class PostLoungeRepositoryTest {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;

    private Post p1; // u1: 10기 / 서울
    private Post p2; // u2: 11기 / 구미
    private Post p3; // u3: 10기 / 구미

    private final PageRequest pageable = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        User u1 = userRepository.save(user("mm-1", "10기", "서울"));
        User u2 = userRepository.save(user("mm-2", "11기", "구미"));
        User u3 = userRepository.save(user("mm-3", "10기", "구미"));

        p1 = postRepository.save(post("서울 10기 글", u1));
        p2 = postRepository.save(post("구미 11기 글", u2));
        p3 = postRepository.save(post("구미 10기 글", u3));
    }

    @Test
    @DisplayName("campus 필터는 같은 캠퍼스 작성자의 글만 조회한다")
    void test_캠퍼스_라운지() {
        // 서울 캠퍼스 → u1의 글만(p1). u2·u3는 구미라 제외
        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, null, "서울", false, pageable);
        assertThat(page.getContent()).extracting(Post::getId).containsExactly(p1.getId());
    }

    @Test
    @DisplayName("cohort 필터는 같은 기수 작성자의 글만 조회한다(캠퍼스 무관)")
    void test_기수_라운지() {
        // 10기 → u1(서울)·u3(구미)의 글(p1, p3). u2는 11기라 제외
        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, "10기", null, false, pageable);
        assertThat(page.getContent()).extracting(Post::getId).containsExactlyInAnyOrder(p1.getId(), p3.getId());
    }

    @Test
    @DisplayName("cohort/campus가 모두 null이면 전체 조회(라운지 미적용)")
    void test_필터없음_전체() {
        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, null, null, false, pageable);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("인기순에도 campus 필터가 동일하게 적용된다")
    void test_인기순_캠퍼스_라운지() {
        Page<Post> page = postRepository.findFilteredPopular(null, null, null, null, null, "구미", false, pageable);
        assertThat(page.getContent()).extracting(Post::getId).containsExactlyInAnyOrder(p2.getId(), p3.getId());
    }

    private User user(String mmUserId, String cohort, String campus) {
        return User.builder()
                .mmUserId(mmUserId).nickname(mmUserId).cohort(cohort).campus(campus)
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
    }

    private Post post(String title, User author) {
        return Post.builder().title(title).content("내용").category(PostCategory.FREE).author(author).build();
    }
}
// [/FEATURE:cohort-campus-lounge]
