package com.company.community.service;

import com.company.community.domain.Comment;
import com.company.community.domain.Post;
import com.company.community.domain.User;
import com.company.community.dto.CommentCreateRequest;
import com.company.community.dto.CommentResponse;
import com.company.community.repository.CommentRepository;
import com.company.community.repository.PostRepository;
import com.company.community.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    /**
     * 댓글 작성 — author는 서버에서만 관리 (익명)
     */
    @Transactional
    public CommentResponse addComment(Long userId, Long postId, CommentCreateRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        Comment comment = Comment.builder()
                .content(request.getContent())
                .post(post)
                .author(author)
                .build();

        Comment saved = commentRepository.save(comment);
        return CommentResponse.from(saved);
    }

    /**
     * 특정 게시글의 댓글 목록 조회 (오래된 순)
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new NoSuchElementException("존재하지 않는 게시글입니다.");
        }

        return commentRepository.findAllByPostIdOrderByCreatedAtAsc(postId).stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }
}
