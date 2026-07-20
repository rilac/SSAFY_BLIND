package com.company.domain.poll.controller;

import com.company.domain.poll.controller.dto.PollResponse;
import com.company.domain.poll.controller.dto.PollVoteRequest;
import com.company.domain.poll.service.PollService;
import com.company.domain.user.entity.User;

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

        return ResponseEntity.ok(pollService.vote(user.getId(), postId, request.getOptionId(), user.getRole()));
    }
}
