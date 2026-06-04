package com.company.domain.poll.controller.dto;

import com.company.domain.poll.entity.PollOption;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// [FEATURE:poll] 익명 투표 결과 — 보기별 집계 + 총 표수 + 내가 고른 보기(없으면 null).
@Getter
@AllArgsConstructor
public class PollResponse {

    private List<Option> options;
    private long totalVotes;
    private Long myOptionId; // null이면 미투표

    @Getter
    @AllArgsConstructor
    public static class Option {
        private Long id;
        private String content;
        private long voteCount;
    }

    // options는 표시 순으로 정렬된 보기, counts는 (optionId, count) Object[] 목록.
    public static PollResponse of(List<PollOption> options, List<Object[]> counts, Long myOptionId) {
        Map<Long, Long> countMap = new HashMap<>();
        long total = 0;
        for (Object[] row : counts) {
            Long optionId = (Long) row[0];
            long c = (Long) row[1];
            countMap.put(optionId, c);
            total += c;
        }
        List<Option> opts = options.stream()
                .map(o -> new Option(o.getId(), o.getContent(), countMap.getOrDefault(o.getId(), 0L)))
                .collect(Collectors.toList());
        return new PollResponse(opts, total, myOptionId);
    }
}
