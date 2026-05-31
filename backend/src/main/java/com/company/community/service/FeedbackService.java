package com.company.community.service;

import com.company.community.domain.Feedback;
import com.company.community.domain.User;
import com.company.community.dto.FeedbackRequest;
import com.company.community.dto.FeedbackResponse;
import com.company.community.repository.FeedbackRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

// 건의함 — 작성(일반 사용자) / 전체 조회(관리자)
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    @Transactional
    public void create(Long userId, FeedbackRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
        feedbackRepository.save(Feedback.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .author(author)
                .build());
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> getAll() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(FeedbackResponse::of)
                .collect(Collectors.toList());
    }
}
