package com.company.domain.post.repository;

import com.company.domain.comment.entity.Comment;
import com.company.domain.comment.repository.CommentRepository;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// [FEATURE:weekly-digest] 인기 점수 랭킹 쿼리 — H2(MySQL 호환)로 실제 JPQL 실행 검증.
// 점수 = 조회수*1 + 반응수*2 + 댓글수*3, 다중 LEFT JOIN의 카티전 곱은 COUNT DISTINCT로 차단함을 확인.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:digestdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
})
class WeeklyDigestRepositoryTest {

    @Autowired private PostRepository postRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PostLikeRepository postLikeRepository;
    @Autowired private CommentRepository commentRepository;

    private final PageRequest top10 = PageRequest.of(0, 10);

    @Test
    @DisplayName("점수=조회*1+반응*2+댓글*3 내림차순으로 정렬된다(반응·댓글 동시 글의 카티전 곱은 DISTINCT로 차단)")
    void test_점수순_정렬_DISTINCT() {
        User author = userRepository.save(user("author"));
        User v1 = userRepository.save(user("v1"));
        User v2 = userRepository.save(user("v2"));

        // postPin: 조회 15, 반응/댓글 0 → 점수 15 (조인 없음 → 카티전 곱 영향 없음)
        Post postPin = postRepository.save(post("핀 글", author, 15));
        // postMixed: 반응 2 + 댓글 2 → 정확 점수 2*2 + 2*3 = 10.
        //   DISTINCT 없으면 2×2=4행으로 부풀어 4*2 + 4*3 = 20이 되어 postPin을 앞지른다 → 순서가 뒤집힘으로 검출.
        Post postMixed = postRepository.save(post("반응댓글 글", author, 0));
        like(postMixed, v1);
        like(postMixed, v2);
        comment(postMixed, v1);
        comment(postMixed, v2);
        // postLow: 조회 3 → 점수 3
        Post postLow = postRepository.save(post("낮은 글", author, 3));

        List<Post> ranked = postRepository.findTopByScoreSince(LocalDateTime.now().minusDays(7), top10);

        assertThat(ranked).extracting(Post::getId)
                .containsExactly(postPin.getId(), postMixed.getId(), postLow.getId());
    }

    @Test
    @DisplayName("Pageable 크기만큼만 Top N을 반환한다")
    void test_TopN_제한() {
        User author = userRepository.save(user("author"));
        postRepository.save(post("a", author, 30));
        postRepository.save(post("b", author, 20));
        postRepository.save(post("c", author, 10));

        List<Post> top2 = postRepository.findTopByScoreSince(LocalDateTime.now().minusDays(7), PageRequest.of(0, 2));

        assertThat(top2).hasSize(2).extracting(Post::getViewCount).containsExactly(30, 20);
    }

    @Test
    @DisplayName("숨김 글은 점수가 높아도 제외된다")
    void test_숨김_제외() {
        User author = userRepository.save(user("author"));
        Post visible = postRepository.save(post("보이는 글", author, 5));
        Post hidden = postRepository.save(hiddenPost("숨김 글", author, 1000));

        List<Post> ranked = postRepository.findTopByScoreSince(LocalDateTime.now().minusDays(7), top10);

        assertThat(ranked).extracting(Post::getId).containsExactly(visible.getId());
        assertThat(ranked).extracting(Post::getId).doesNotContain(hidden.getId());
    }

    @Test
    @DisplayName("집계 기간(since) 이후 작성 글만 집계한다 — since가 미래면 결과 없음")
    void test_기간_필터() {
        User author = userRepository.save(user("author"));
        postRepository.save(post("최근 글", author, 10));

        // 방금 저장한 글의 createdAt(now)보다 미래의 since → 윈도우 밖 → 빈 결과
        List<Post> ranked = postRepository.findTopByScoreSince(LocalDateTime.now().plusDays(1), top10);

        assertThat(ranked).isEmpty();
    }

    private User user(String mm) {
        return User.builder().mmUserId(mm).nickname(mm).cohort("10기").campus("서울")
                .status(UserStatus.ACTIVE).role(UserRole.USER).build();
    }

    private Post post(String title, User author, int viewCount) {
        return Post.builder().title(title).content("내용").category(PostCategory.FREE)
                .author(author).viewCount(viewCount).build();
    }

    private Post hiddenPost(String title, User author, int viewCount) {
        return Post.builder().title(title).content("내용").category(PostCategory.FREE)
                .author(author).viewCount(viewCount).hidden(true).build();
    }

    private void like(Post post, User user) {
        postLikeRepository.save(PostLike.builder().post(post).user(user).build());
    }

    private void comment(Post post, User author) {
        commentRepository.save(Comment.builder().content("댓글").post(post).author(author).build());
    }
}
// [/FEATURE:weekly-digest]
