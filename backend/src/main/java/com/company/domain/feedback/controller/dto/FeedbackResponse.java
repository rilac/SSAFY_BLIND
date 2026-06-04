package com.company.domain.feedback.controller.dto;

import com.company.domain.feedback.entity.Feedback;
import com.company.domain.feedback.entity.FeedbackStatus;
import com.company.global.dto.AuthorInfo;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

// 건의함 응답 — 관리자 열람용. 작성자는 가명(AuthorInfo)으로만 표시.
@Getter
@AllArgsConstructor
public class FeedbackResponse {

    private Long id;
    private String title;
    private String content;
    private AuthorInfo author;
    private LocalDateTime createdAt;
    private FeedbackStatus status;

    public static FeedbackResponse of(Feedback feedback) {
        return new FeedbackResponse(
                feedback.getId(),
                feedback.getTitle(),
                feedback.getContent(),
                AuthorInfo.of(feedback.getAuthor()),
                feedback.getCreatedAt(),
                feedback.getStatus()
        );
    }
}
