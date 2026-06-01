package com.company.community.controller;

import com.company.community.domain.User;
import com.company.community.dto.PollResponse;
import com.company.community.dto.PollVoteRequest;
import com.company.community.service.PollService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

// [FEATURE:poll] 익명 투표 — 투표/변경/취소(토글). 결과는 보기별 집계만 반환(익명).
@RestController
@RequestMapping("/api/posts/{postId}/poll")
@RequiredArgsConstructor
public class PollController {

    private final PollService pollService;

    @PostMapping("/vote")
    public ResponseEntity<PollResponse> vote(
            @AuthenticationPrincipal User user,
            @PathVariable Long postId,
            @Valid @RequestBody PollVoteRequest request) {

        return ResponseEntity.ok(pollService.vote(user.getId(), postId, request.getOptionId()));
    }
}
