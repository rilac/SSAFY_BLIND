package com.company.domain.post.controller.dto;

import com.company.domain.post.entity.PostCategory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

// (#3) 게시글 수정 요청 DTO
// PostCreateRequest와 필드가 같더라도 의도와 용도가 다르므로 별도 클래스로 분리
@Getter
@NoArgsConstructor
public class PostUpdateRequest {

    @NotNull(message = "카테고리를 선택해주세요.")
    private PostCategory category;

    // H-NEW-1: title은 VARCHAR(255)이므로 상한을 두지 않으면 256자↑ 입력 시 DB 제약 위반 500
    @NotBlank(message = "제목을 입력해주세요.")
    @Size(max = 200, message = "제목은 200자 이하로 입력해주세요.")
    private String title;

    @NotBlank(message = "내용을 입력해주세요.")
    @Size(max = 10000, message = "내용은 10000자 이하로 입력해주세요.")
    private String content;
}
