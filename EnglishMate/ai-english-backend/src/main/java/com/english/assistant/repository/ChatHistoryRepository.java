package com.english.assistant.repository;

import com.english.assistant.entity.ChatHistory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    @Query("""
            select h
              from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
               and h.createTime >= :accountCreatedAt
             order by h.createTime asc
            """)
    List<ChatHistory> findOwnedHistory(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select h
              from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
               and h.createTime >= :accountCreatedAt
             order by h.createTime desc
            """)
    List<ChatHistory> findOwnedRecentHistory(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            Pageable pageable
    );

    @Query("""
            select h
              from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
               and h.requestId = :requestId
               and h.role = :role
               and h.createTime >= :accountCreatedAt
             order by h.createTime desc
            """)
    List<ChatHistory> findOwnedRequestMessages(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId,
            @Param("requestId") String requestId,
            @Param("role") String role,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            Pageable pageable
    );

    default Optional<ChatHistory> findFirstOwnedRequestMessage(
            Long userId,
            String sessionId,
            String requestId,
            String role,
            LocalDateTime accountCreatedAt
    ) {
        return findOwnedRequestMessages(userId, sessionId, requestId, role, accountCreatedAt, Pageable.ofSize(1))
                .stream()
                .findFirst();
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from ChatHistory h
             where h.userId = :userId
               and h.sessionId = :sessionId
            """)
    int deleteOwnedSessionHistory(
            @Param("userId") Long userId,
            @Param("sessionId") String sessionId
    );

    long countByUserId(Long userId);

    long countByUserIdAndRole(Long userId, String role);
}
