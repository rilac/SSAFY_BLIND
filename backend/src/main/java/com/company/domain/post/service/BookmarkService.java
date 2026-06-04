package com.company.domain.post.service;

import com.company.domain.post.controller.dto.BookmarkResponse;
import com.company.domain.post.entity.Bookmark;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostLike;
import com.company.domain.post.repository.BookmarkRepository;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;

// 스크랩 토글 — PostLike 토글 패턴 동일(동시성 안전 처리 포함)
@Service
@RequiredArgsConstructor
public class BookmarkService {

    private final BookmarkRepository bookmarkRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;

    @Transactional
    public BookmarkResponse toggle(Long userId, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));

        Optional<Bookmark> existing = bookmarkRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return new BookmarkResponse(false);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 유저입니다."));
        try {
            bookmarkRepository.save(Bookmark.builder().post(post).user(user).build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청 유니크 위반 — 이미 스크랩 상태로 간주
            return new BookmarkResponse(true);
        }
        return new BookmarkResponse(true);
    }
}
