package com.company.community.repository;

import com.company.community.domain.Bookmark;
import com.company.community.domain.Post;
import com.company.community.domain.PostCategory;
import com.company.community.domain.PostLike;
import com.company.community.domain.User;
import com.company.community.domain.UserRole;
import com.company.community.domain.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 통합 목록 쿼리(카테고리/검색/scope/정렬) 검증 — H2(MySQL 호환 모드)로 실제 JPQL 실행
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", // M-NEW-7: 베이스라인은 MySQL 전용 — H2 테스트에선 Flyway 미실행
})
class PostRepositoryTest {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private BookmarkRepository bookmarkRepository;

    private User u1;
    private User u2;
    private Post p1; // FREE, u1
    private Post p2; // JOB, u2, 좋아요 2
    private Post p3; // FREE, u1, 본문에 "도커"

    private final PageRequest pageable = PageRequest.of(0, 10);

    @BeforeEach
    void setUp() {
        u1 = userRepository.save(User.builder().mmUserId("mm-1").status(UserStatus.ACTIVE).role(UserRole.USER).build());
        u2 = userRepository.save(User.builder().mmUserId("mm-2").status(UserStatus.ACTIVE).role(UserRole.USER).build());

        p1 = postRepository.save(Post.builder().title("자유 글").content("자유로운 내용").category(PostCategory.FREE).author(u1).build());
        p2 = postRepository.save(Post.builder().title("채용 공고").content("백엔드 채용").category(PostCategory.JOB).author(u2).build());
        p3 = postRepository.save(Post.builder().title("질문 아닌 자유").content("도커 컴포즈 설정 질문").category(PostCategory.FREE).author(u1).build());

        // p2 좋아요 2개 (u1, u2)
        postLikeRepository.save(PostLike.builder().post(p2).user(u1).build());
        postLikeRepository.save(PostLike.builder().post(p2).user(u2).build());

        // u1이 p2 스크랩
        bookmarkRepository.save(Bookmark.builder().post(p2).user(u1).build());
    }

    @Test
    @DisplayName("필터 없으면 전체 게시글이 조회된다")
    void test_전체조회() {
        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, null, null, false, pageable);
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("카테고리 필터가 적용된다 (FREE 2건)")
    void test_카테고리_필터() {
        Page<Post> page = postRepository.findFilteredLatest(PostCategory.FREE, null, null, null, null, null, false, pageable);
        assertThat(page.getContent()).extracting(Post::getCategory).containsOnly(PostCategory.FREE);
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("키워드 검색이 제목·본문에 적용된다")
    void test_키워드_검색() {
        Page<Post> page = postRepository.findFilteredLatest(null, "도커", null, null, null, null, false, pageable);
        assertThat(page.getContent()).extracting(Post::getId).containsExactly(p3.getId());
    }

    @Test
    @DisplayName("scope=mine 은 작성자 본인 글만 조회한다")
    void test_내가쓴글() {
        Page<Post> page = postRepository.findFilteredLatest(null, null, u1.getId(), null, null, null, false, pageable);
        assertThat(page.getContent()).extracting(Post::getId).containsExactlyInAnyOrder(p1.getId(), p3.getId());
    }

    @Test
    @DisplayName("scope=bookmarked 는 내가 스크랩한 글만 조회한다")
    void test_스크랩한글() {
        Page<Post> page = postRepository.findFilteredLatest(null, null, null, u1.getId(), null, null, false, pageable);
        assertThat(page.getContent()).extracting(Post::getId).containsExactly(p2.getId());
    }

    @Test
    @DisplayName("인기순은 좋아요가 많은 글이 먼저 온다")
    void test_인기순() {
        Page<Post> page = postRepository.findFilteredPopular(null, null, null, null, null, null, false, pageable);
        assertThat(page.getContent().get(0).getId()).isEqualTo(p2.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("숨김(hidden) 처리된 글은 일반 목록에서 제외된다")
    void test_숨김글_제외() {
        p2.hide();
        postRepository.save(p2);

        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, null, null, false, pageable);

        assertThat(page.getContent()).extracting(Post::getId).doesNotContain(p2.getId());
        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    // [FEATURE:admin-moderation] 관리자(includeHidden=true)는 숨김 글도 목록에서 본다.
    @Test
    @DisplayName("includeHidden=true(관리자)면 숨김 글도 목록에 포함된다")
    void test_관리자_숨김글_포함() {
        p2.hide();
        postRepository.save(p2);

        Page<Post> page = postRepository.findFilteredLatest(null, null, null, null, null, null, true, pageable);

        assertThat(page.getContent()).extracting(Post::getId).contains(p2.getId());
        assertThat(page.getTotalElements()).isEqualTo(3);
    }
}
