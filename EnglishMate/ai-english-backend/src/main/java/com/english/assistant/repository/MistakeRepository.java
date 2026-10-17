package com.english.assistant.repository;

import com.english.assistant.entity.Mistake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MistakeRepository extends JpaRepository<Mistake, Long> {

    @Query("""
            select m
              from Mistake m
             where m.userId = :userId
               and m.createTime >= :accountCreatedAt
               and m.essayRecordId is not null
               and m.originalText is not null
               and m.originalText <> ''
               and m.correctedText is not null
               and m.correctedText <> ''
               and m.explanation is not null
               and m.explanation <> ''
             order by m.createTime desc
            """)
    List<Mistake> findValidOwnedMistakes(
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select m
              from Mistake m
             where m.id = :id
               and m.userId = :userId
               and m.createTime >= :accountCreatedAt
            """)
    Optional<Mistake> findOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select m
              from Mistake m
             where m.userId = :userId
               and m.contentHash = :contentHash
               and m.createTime >= :accountCreatedAt
            """)
    Optional<Mistake> findOwnedByContentHash(
            @Param("userId") Long userId,
            @Param("contentHash") String contentHash,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update Mistake m
               set m.mastered = :value
             where m.id = :id
               and m.userId = :userId
               and m.createTime >= :accountCreatedAt
            """)
    int updateOwnedMastered(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt,
            @Param("value") boolean value
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from Mistake m
             where m.id = :id
               and m.userId = :userId
               and m.createTime >= :accountCreatedAt
            """)
    int deleteOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    long countByUserId(Long userId);
}
