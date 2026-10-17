package com.english.assistant.repository;

import com.english.assistant.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    @Query("""
            select s
              from ChatSession s
             where s.userId = :userId
               and s.createTime >= :accountCreatedAt
             order by s.updateTime desc
            """)
    List<ChatSession> findOwnedSessions(
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select s
              from ChatSession s
             where s.id = :id
               and s.userId = :userId
               and s.createTime >= :accountCreatedAt
            """)
    Optional<ChatSession> findOwnedById(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update ChatSession s
               set s.title = :title,
                   s.updateTime = :updateTime
             where s.id = :id
               and s.userId = :userId
               and s.createTime >= :accountCreatedAt
            """)
    int updateOwnedSessionMeta(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            @Param("title") String title,
            @Param("updateTime") LocalDateTime updateTime
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from ChatSession s
             where s.id = :id
               and s.userId = :userId
               and s.createTime >= :accountCreatedAt
            """)
    int deleteOwnedById(
            @Param("id") String id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

}
