package com.company.domain.poll.service;

import com.company.domain.poll.controller.dto.PollResponse;
import com.company.domain.poll.entity.PollOption;
import com.company.domain.poll.entity.PollVote;
import com.company.domain.poll.repository.PollOptionRepository;
import com.company.domain.poll.repository.PollVoteRepository;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostCategory;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.entity.UserStatus;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidStateException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// [FEATURE:poll] 익명 투표 서비스 단위 테스트 — 보기 생성/검증, 투표 신규·변경·취소, 잘못된 보기 거부
@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock private PollOptionRepository pollOptionRepository;
    @Mock private PollVoteRepository pollVoteRepository;
    @Mock private PostRepository postRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private PollService pollService;

    @Test
    @DisplayName("createOptions: 2개 이상 보기를 순서대로 저장(공백 제거)")
    void createOptions_정상() {
        Post post = post(10L);

        pollService.createOptions(post, List.of(" React ", "Vue", ""));

        ArgumentCaptor<List<PollOption>> cap = ArgumentCaptor.forClass(List.class);
        verify(pollOptionRepository).saveAll(cap.capture());
        List<PollOption> saved = cap.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).getContent()).isEqualTo("React");
        assertThat(saved.get(0).getSortOrder()).isEqualTo(0);
        assertThat(saved.get(1).getContent()).isEqualTo("Vue");
        assertThat(saved.get(1).getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("createOptions: null/빈 목록이면 투표 미생성(저장 안 함)")
    void createOptions_없음() {
        pollService.createOptions(post(10L), null);
        pollService.createOptions(post(10L), List.of("", "   "));
        verify(pollOptionRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("createOptions: 유효 보기가 1개면 InvalidStateException")
    void createOptions_1개_거부() {
        assertThatThrownBy(() -> pollService.createOptions(post(10L), List.of("혼자")))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    @DisplayName("createOptions: 보기가 8개 초과면 InvalidStateException")
    void createOptions_초과_거부() {
        List<String> nine = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertThatThrownBy(() -> pollService.createOptions(post(10L), nine))
                .isInstanceOf(InvalidStateException.class);
    }

    @Test
    @DisplayName("vote: 처음 투표하면 새 표를 저장")
    void vote_신규() {
        given(pollOptionRepository.findByPostIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(option(100L), option(101L)));
        given(pollVoteRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.empty());
        given(userRepository.existsById(1L)).willReturn(true);
        given(postRepository.findById(10L)).willReturn(Optional.of(post(10L)));
        given(pollVoteRepository.countByPostIdGroupByOption(10L)).willReturn(List.of());

        pollService.vote(1L, 10L, 100L, UserRole.USER);

        verify(pollVoteRepository).insertIgnore(eq(10L), eq(1L), eq(100L), any(LocalDateTime.class));
        verify(pollVoteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("vote: 이미 고른 보기를 다시 누르면 취소(delete)")
    void vote_취소() {
        PollVote existing = PollVote.builder().optionId(100L).build();
        given(pollOptionRepository.findByPostIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(option(100L), option(101L)));
        given(pollVoteRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.of(existing));
        given(pollVoteRepository.countByPostIdGroupByOption(10L)).willReturn(List.of());
        given(postRepository.findById(10L)).willReturn(Optional.of(post(10L))); // 숨김 게이트가 항상 post를 로드

        pollService.vote(1L, 10L, 100L, UserRole.USER);

        verify(pollVoteRepository).delete(existing);
        verify(pollVoteRepository, never()).insertIgnore(any(), any(), any(), any());
    }

    @Test
    @DisplayName("vote: 다른 보기를 누르면 변경(changeOption, delete/save 없음)")
    void vote_변경() {
        PollVote existing = PollVote.builder().optionId(100L).build();
        given(pollOptionRepository.findByPostIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(option(100L), option(101L)));
        given(pollVoteRepository.findByPostIdAndUserId(10L, 1L)).willReturn(Optional.of(existing));
        given(pollVoteRepository.countByPostIdGroupByOption(10L)).willReturn(List.of());
        given(postRepository.findById(10L)).willReturn(Optional.of(post(10L))); // 숨김 게이트가 항상 post를 로드

        pollService.vote(1L, 10L, 101L, UserRole.USER);

        assertThat(existing.getOptionId()).isEqualTo(101L);
        verify(pollVoteRepository, never()).delete(any());
        verify(pollVoteRepository, never()).insertIgnore(any(), any(), any(), any());
    }

    @Test
    @DisplayName("vote: 해당 글의 보기가 아니면 NoSuchElementException")
    void vote_잘못된보기() {
        given(pollOptionRepository.findByPostIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(option(100L), option(101L)));
        given(postRepository.findById(10L)).willReturn(Optional.of(post(10L))); // 숨김 게이트가 항상 post를 로드

        assertThatThrownBy(() -> pollService.vote(1L, 10L, 999L, UserRole.USER))
                .isInstanceOf(NoSuchElementException.class);
        verify(pollVoteRepository, never()).insertIgnore(any(), any(), any(), any());
    }

    @Test
    @DisplayName("vote: 투표가 없는 글이면 NoSuchElementException")
    void vote_투표없음() {
        given(pollOptionRepository.findByPostIdOrderBySortOrderAsc(10L)).willReturn(List.of());
        given(postRepository.findById(10L)).willReturn(Optional.of(post(10L))); // 숨김 게이트가 항상 post를 로드

        assertThatThrownBy(() -> pollService.vote(1L, 10L, 100L, UserRole.USER))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("buildResults: 투표 없는 글은 null")
    void buildResults_없으면_null() {
        given(pollOptionRepository.findByPostIdOrderBySortOrderAsc(10L)).willReturn(List.of());
        assertThat(pollService.buildResults(10L, 1L)).isNull();
    }

    @Test
    @DisplayName("buildResults: 보기별 집계 + 총 표수 + 내 선택")
    void buildResults_집계() {
        given(pollOptionRepository.findByPostIdOrderBySortOrderAsc(10L))
                .willReturn(List.of(option(100L), option(101L)));
        given(pollVoteRepository.countByPostIdGroupByOption(10L))
                .willReturn(List.of(new Object[]{100L, 3L}, new Object[]{101L, 1L}));
        given(pollVoteRepository.findByPostIdAndUserId(10L, 1L))
                .willReturn(Optional.of(PollVote.builder().optionId(100L).build()));

        PollResponse res = pollService.buildResults(10L, 1L);

        assertThat(res.getTotalVotes()).isEqualTo(4L);
        assertThat(res.getMyOptionId()).isEqualTo(100L);
        assertThat(res.getOptions()).extracting(PollResponse.Option::getVoteCount).containsExactly(3L, 1L);
    }

    // --- helpers ---

    private PollOption option(Long id) {
        PollOption o = PollOption.builder().content("opt" + id).sortOrder(0).build();
        setId(o, id);
        return o;
    }

    private Post post(Long id) {
        Post p = Post.builder().title("t").content("c").category(PostCategory.FREE).build();
        setId(p, id);
        return p;
    }

    private User user(Long id) {
        User u = User.builder().mmUserId("mm-" + id).status(UserStatus.ACTIVE).role(UserRole.USER).build();
        setId(u, id);
        return u;
    }

    private void setId(Object obj, Long id) {
        try {
            var f = obj.getClass().getDeclaredField("id");
            f.setAccessible(true);
            f.set(obj, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
// [/FEATURE:poll]
