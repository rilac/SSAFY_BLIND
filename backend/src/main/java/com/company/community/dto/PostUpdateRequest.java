package com.company.community.dto;

import com.company.community.domain.PostCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

// (#3) 게시글 수정 요청 DTO
// PostCreateRequest와 필드가 같더라도 의도와 용도가 다르므로 별도 클래스로 분리
@Getter
@NoArgsConstructor
public class PostUpdateRequest {

    @NotNull(message = "카테고리를 선택해주세요.")
    private PostCategory category;

    @NotBlank(message = "제목을 입력해주세요.")
    private String title;

    @NotBlank(message = "내용을 입력해주세요.")
    private String content;
}
