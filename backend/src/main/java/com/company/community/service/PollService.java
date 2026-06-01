package com.company.community.service;

import com.company.community.domain.PollOption;
import com.company.community.domain.PollVote;
import com.company.community.domain.Post;
import com.company.community.domain.User;
import com.company.community.dto.PollResponse;
import com.company.community.exception.InvalidStateException;
import com.company.community.repository.PollOptionRepository;
import com.company.community.repository.PollVoteRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// [FEATURE:poll] 익명 투표 도메인 서비스 — 작성 시 보기 생성, 투표(1인 1표·변경·취소), 집계, 삭제 정리.
@Service
@RequiredArgsConstructor
public class PollService {

    static final int MIN_OPTIONS = 2;
    static final int MAX_OPTIONS = 8;
    static final int MAX_OPTION_LEN = 100;

    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    // 게시글 작성 시 투표 보기 생성. rawOptions가 null/빈값이면 투표 없음(스킵).
    // 공백 제거 후 2~8개, 각 100자 이하 — 위반 시 InvalidState(작성 트랜잭션 롤백).
    @Transactional
    public void createOptions(Post post, List<String> rawOptions) {
        if (rawOptions == null) return;
        List<String> cleaned = rawOptions.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(Collectors.toList());
        if (cleaned.isEmpty()) return; // 투표 미첨부
        if (cleaned.size() < MIN_OPTIONS) {
            throw new InvalidStateException("투표는 보기가 " + MIN_OPTIONS + "개 이상이어야 합니다.");
        }
        if (cleaned.size() > MAX_OPTIONS) {
            throw new InvalidStateException("투표 보기는 최대 " + MAX_OPTIONS + "개입니다.");
        }
        for (String c : cleaned) {
            if (c.length() > MAX_OPTION_LEN) {
                throw new InvalidStateException("보기는 " + MAX_OPTION_LEN + "자 이하로 입력해주세요.");
            }
        }
        List<PollOption> options = new ArrayList<>();
        for (int i = 0; i < cleaned.size(); i++) {
            options.add(PollOption.builder().post(post).content(cleaned.get(i)).sortOrder(i).build());
        }
        pollOptionRepository.saveAll(options);
    }

    // 투표 토글 — 같은 보기 재클릭=취소, 다른 보기=변경, 처음=신규. 1인 1표.
    @Transactional
    public PollResponse vote(Long userId, Long postId, Long optionId) {
        List<PollOption> options = pollOptionRepository.findByPostIdOrderBySortOrderAsc(postId);
        if (options.isEmpty()) {
            throw new NoSuchElementException("투표가 없는 게시글입니다.");
        }
        if (options.stream().noneMatch(o -> o.getId().equals(optionId))) {
            throw new NoSuchElementException("존재하지 않는 보기입니다.");
        }

        Optional<PollVote> existing = pollVoteRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            PollVote v = existing.get();
            if (v.getOptionId().equals(optionId)) {
                pollVoteRepository.delete(v); // 같은 보기 재클릭 → 투표 취소
            } else {
                v.changeOption(optionId); // 다른 보기 → 변경(dirty checking)
            }
        } else {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
            Post post = postRepository.findById(postId)
                    .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
            try {
                pollVoteRepository.save(PollVote.builder().post(post).user(user).optionId(optionId).build());
            } catch (DataIntegrityViolationException e) {
                // 동시 첫 투표 경합 — 이미 투표된 것으로 간주(유니크 제약)
            }
        }
        return buildResults(postId, userId, options);
    }

    // 게시글 응답에 실을 투표 결과(투표 없으면 null).
    @Transactional(readOnly = true)
    public PollResponse buildResults(Long postId, Long userId) {
        List<PollOption> options = pollOptionRepository.findByPostIdOrderBySortOrderAsc(postId);
        if (options.isEmpty()) return null;
        return buildResults(postId, userId, options);
    }

    private PollResponse buildResults(Long postId, Long userId, List<PollOption> options) {
        List<Object[]> counts = pollVoteRepository.countByPostIdGroupByOption(postId);
        Long myOptionId = pollVoteRepository.findByPostIdAndUserId(postId, userId)
                .map(PollVote::getOptionId)
                .orElse(null);
        return PollResponse.of(options, counts, myOptionId);
    }

    // 목록용 — 투표가 있는 글 id 집합(배치, N+1 방지).
    @Transactional(readOnly = true)
    public Set<Long> hasPollPostIds(List<Long> postIds) {
        if (postIds.isEmpty()) return Set.of();
        return new HashSet<>(pollOptionRepository.findPostIdsWithPoll(postIds));
    }

    // 게시글 삭제 시 투표 표·보기 정리(둘 다 post_id FK → 글 삭제 전에 호출).
    @Transactional
    public void deleteForPost(Long postId) {
        pollVoteRepository.deleteByPostId(postId);
        pollOptionRepository.deleteByPostId(postId);
    }
}
