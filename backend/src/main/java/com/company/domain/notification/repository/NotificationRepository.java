package com.company.domain.notification.repository;

import com.company.domain.notification.entity.Notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    // boolean 파생 쿼리 네이밍 모호성 회피 — 명시적 JPQL
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient.id = :userId AND n.isRead = false")
    long countUnread(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :userId AND n.isRead = false")
    void markAllRead(@Param("userId") Long userId);

    // C-NEW-1: 게시글 삭제 시 해당 글을 가리키는 알림 정리(고아 알림 방지).
    // Notification.postId는 FK가 아닌 단순 Long이라 삭제를 막지는 않으나, 죽은 링크가 남는다.
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.postId = :postId")
    void deleteByPostId(@Param("postId") Long postId);
}
