package com.company.community.service;

import com.company.community.domain.Post;
import com.company.community.domain.User;
import com.company.community.dto.*;
import com.company.community.exception.ForbiddenException;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 게시글 작성 — author는 서버에서만 관리, 응답에는 노출하지 않음
     */
    @Transactional
    public PostResponse createPost(Long userId, PostCreateRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));

        Post post = Post.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .author(author)
                .build();

        Post saved = postRepository.save(post);
        return PostResponse.from(saved);
    }

    /**
     * 게시글 상세 조회
     * ★ 벌크 UPDATE로 조회수 증가 → race condition 방지 (#4)
     */
    @Transactional
    public PostResponse getPost(Long postId) {
        // 먼저 벌크 UPDATE로 조회수 증가 (DB 레벨에서 원자적)
        postRepository.incrementViewCount(postId);

        // 그 다음 조회 (증가된 viewCount 반영됨)
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        return PostResponse.from(post);
    }

    /**
     * 게시글 목록 — 페이지네이션 (#10) + N+1 해결 (#3)
     * JPQL JOIN 쿼리로 게시글 + 댓글 수를 한 번에 조회
     */
    @Transactional(readOnly = true)
    public PageResponse<PostListResponse> getAllPosts(int page, int size) {
        Page<Object[]> resultPage = postRepository.findAllWithCommentCount(
                PageRequest.of(page, size)
        );

        // Object[0] = Post, Object[1] = COUNT(c)
        List<PostListResponse> content = resultPage.getContent().stream()
                .map(row -> {
                    Post post = (Post) row[0];
                    long commentCount = (Long) row[1];
                    return PostListResponse.of(post, commentCount);
                })
                .collect(Collectors.toList());

        return PageResponse.of(resultPage, content);
    }

    /**
     * 게시글 삭제 — 본인만 삭제 가능
     * ★ cascade = ALL로 연관 댓글 자동 삭제 (#5)
     */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        // 본인 글인지 검증
        if (!post.getAuthor().getId().equals(userId)) {
            throw new ForbiddenException("본인의 글만 삭제할 수 있습니다.");
        }

        // cascade로 연관 댓글도 자동 삭제됨
        postRepository.delete(post);
    }
}
