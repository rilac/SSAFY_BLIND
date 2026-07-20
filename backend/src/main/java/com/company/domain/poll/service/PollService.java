package com.company.domain.poll.service;

import com.company.domain.poll.controller.dto.PollResponse;
import com.company.domain.poll.entity.PollOption;
import com.company.domain.poll.entity.PollVote;
import com.company.domain.poll.repository.PollOptionRepository;
import com.company.domain.poll.repository.PollVoteRepository;
import com.company.domain.post.entity.Post;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.repository.UserRepository;
import com.company.global.exception.InvalidStateException;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.time.LocalDateTime;
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
    public PollResponse vote(Long userId, Long postId, Long optionId, UserRole role) {
        // [FEATURE:hidden-author-visibility] post 로드를 최상단으로 올려 숨김 게이트를 먼저 통과시킨다.
        // 아래 신규 투표 분기의 중복 findById를 흡수하므로 신규 경로의 쿼리 수는 그대로다.
        // 순서 주의: 숨김 판정이 "투표가 없는 게시글" 검사보다 앞서야 제3자에게 글의 존재가 새지 않는다.
        Post gatePost = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        gatePost.assertWritable(role, userId);

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
            // INSERT IGNORE가 FK 위반까지 삼키므로 존재 확인 가드를 남긴다(제거 금지).
            if (!userRepository.existsById(userId)) {
                throw new NoSuchElementException("존재하지 않는 유저입니다.");
            }
            // 멱등 삽입 — 동시 첫 투표 경합이면 0행(1인 1표는 유니크 제약이 보장).
            pollVoteRepository.insertIgnore(postId, userId, optionId, LocalDateTime.now());
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
