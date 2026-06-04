package com.company.domain.post.repository;

import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.entity.PostLike;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// [FEATURE:pinned-posts] 공지 고정 정렬 — H2(MySQL 호환)로 실제 JPQL 실행 검증.
// pinned=true 글이 최신순/인기순 모두에서 항상 최상단(필터 결과 내)에 오는지 확인.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pinneddb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
})
class PostPinnedRepositoryTest {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostLikeRepository postLikeRepository;

    private final PageRequest pageable = PageRequest.of(0, 10);

    @Test
    @DisplayName("최신순에서 고정 글이 다른 글보다 항상 먼저 온다")
    void test_최신순_고정_최상단() {
        User u = userRepository.save(user("u1"));
        postRepository.save(post("일반 글 A", u, false));
        Post pinned = postRepository.save(post("공지 글", u, true));
        postRepository.save(post("일반 글 B", u, false));

        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, null, null, false, pageable);

        assertThat(page.getContent().get(0).getId()).isEqualTo(pinned.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("인기순에서 고정 글은 좋아요가 더 많은 글보다도 먼저 온다")
    void test_인기순_고정_우선() {
        User u = userRepository.save(user("u1"));
        User v1 = userRepository.save(user("v1"));
        User v2 = userRepository.save(user("v2"));

        // 고정 글: 좋아요 0. 비고정 인기 글: 좋아요 2 → pinned가 없으면 인기 글이 먼저지만, pinned가 우선이어야 한다.
        Post pinned = postRepository.save(post("공지 글", u, true));
        Post popular = postRepository.save(post("인기 글", u, false));
        postLikeRepository.save(PostLike.builder().post(popular).user(v1).build());
        postLikeRepository.save(PostLike.builder().post(popular).user(v2).build());

        Page<Post> page = postRepository.findFilteredPopular(null, null, null, null, null, null, false, pageable);

        assertThat(page.getContent().get(0).getId()).isEqualTo(pinned.getId());
        assertThat(page.getContent().get(1).getId()).isEqualTo(popular.getId());
    }

    @Test
    @DisplayName("고정 글이 없으면 기존 정렬(최신순)대로 동작한다")
    void test_고정없음_기존정렬() {
        User u = userRepository.save(user("u1"));
        postRepository.save(post("글", u, false));
        postRepository.save(post("글", u, false));

        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, null, null, false, pageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    private User user(String mm) {
        return User.builder().mmUserId(mm).nickname(mm).cohort("10기").campus("서울")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
    }

    private Post post(String title, User author, boolean pinned) {
        return Post.builder().title(title).content("내용").category(PostCategory.FREE)
                .author(author).pinned(pinned).build();
    }
}
// [/FEATURE:pinned-posts]
