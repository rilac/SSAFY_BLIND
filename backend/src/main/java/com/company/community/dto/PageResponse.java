package com.company.community.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지네이션 응답 래퍼.
 * Spring의 Page 객체를 프론트엔드에 필요한 형태로 변환.
 */
@Getter
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;       // 실제 데이터 리스트
    private int totalPages;        // 전체 페이지 수
    private long totalElements;    // 전체 요소 수
    private int currentPage;       // 현재 페이지 번호 (0-based)
    private boolean hasNext;       // 다음 페이지 존재 여부

    /**
     * Spring Page 객체와 변환된 content 리스트로 생성
     */
    public static <T> PageResponse<T> of(Page<?> page, List<T> content) {
        return new PageResponse<>(
                content,
                page.getTotalPages(),
                page.getTotalElements(),
                page.getNumber(),
                page.hasNext()
        );
    }
}
