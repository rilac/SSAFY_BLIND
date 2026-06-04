package com.company.domain.post.controller.dto;

import com.company.domain.post.entity.ReactionType;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// [FEATURE:reactions] 게시글 반응 결과 — 종류별 수(전체 4종, 0 포함) + 총합 + 내가 누른 반응(없으면 null).
@Getter
@AllArgsConstructor
public class ReactionResponse {

    private List<Item> reactions; // ReactionType 순서대로 4종(count 0 포함)
    private long total;
    private String myReaction;    // 내가 누른 반응 종류명, 미반응이면 null

    @Getter
    @AllArgsConstructor
    public static class Item {
        private String type;  // ReactionType.name()
        private long count;
    }

    // typeCounts: (ReactionType, Long) 목록. myType: 현재 유저가 누른 종류(null 가능).
    public static ReactionResponse of(List<Object[]> typeCounts, ReactionType myType) {
        Map<ReactionType, Long> byType = new EnumMap<>(ReactionType.class);
        for (Object[] row : typeCounts) {
            byType.put((ReactionType) row[0], (Long) row[1]);
        }
        long total = 0;
        List<Item> items = new ArrayList<>();
        for (ReactionType t : ReactionType.values()) {
            long c = byType.getOrDefault(t, 0L);
            total += c;
            items.add(new Item(t.name(), c));
        }
        return new ReactionResponse(items, total, myType == null ? null : myType.name());
    }
}
