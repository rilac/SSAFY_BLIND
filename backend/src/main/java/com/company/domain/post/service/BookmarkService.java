package com.company.domain.post.service;

import com.company.domain.post.controller.dto.BookmarkResponse;
import com.company.domain.post.entity.Bookmark;
import com.company.domain.post.entity.Post;
import com.company.domain.post.entity.PostLike;
import com.company.domain.post.repository.BookmarkRepository;
import com.company.domain.post.repository.PostRepository;
import com.company.domain.user.entity.User;
import com.company.domain.user.entity.UserRole;
import com.company.domain.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
    public BookmarkResponse toggle(Long userId, Long postId, UserRole role) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NoSuchElementException("존재하지 않는 게시글입니다."));
        // [FEATURE:hidden-author-visibility] 숨김 글은 스크랩 토글 불가.
        post.assertWritable(role, userId);

        Optional<Bookmark> existing = bookmarkRepository.findByPostIdAndUserId(postId, userId);
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            return new BookmarkResponse(false);
        }

        // INSERT IGNORE가 FK 위반까지 삼키므로 존재 확인 가드를 남긴다(제거 금지).
        if (!userRepository.existsById(userId)) {
            throw new NoSuchElementException("존재하지 않는 유저입니다.");
        }
        // 멱등 삽입 — 동시 요청이어도 최종 상태는 "스크랩됨"으로 동일. 예외가 없어 트랜잭션이 오염되지 않는다.
        bookmarkRepository.insertIgnore(postId, userId, LocalDateTime.now());
        return new BookmarkResponse(true);
    }
}
