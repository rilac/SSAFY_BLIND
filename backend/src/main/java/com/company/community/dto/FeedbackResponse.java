package com.company.community.dto;

import com.company.community.domain.Feedback;
import com.company.community.domain.FeedbackStatus;
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
