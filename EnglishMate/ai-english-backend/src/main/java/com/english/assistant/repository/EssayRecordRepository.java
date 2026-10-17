package com.english.assistant.repository;

import com.english.assistant.entity.EssayRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EssayRecordRepository extends JpaRepository<EssayRecord, Long> {

    @Query("""
            select e
              from EssayRecord e
             where e.userId = :userId
               and e.createTime >= :accountCreatedAt
               and e.level is not null
               and e.level <> ''
               and e.originalContent is not null
               and e.originalContent <> ''
               and e.resultJson is not null
               and e.resultJson <> ''
               and e.score is not null
             order by e.createTime desc
            """)
    List<EssayRecord> findValidOwnedRecords(
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select e
              from EssayRecord e
             where e.id = :id
               and e.userId = :userId
               and e.createTime >= :accountCreatedAt
            """)
    Optional<EssayRecord> findOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    @Query("""
            select e
              from EssayRecord e
             where e.userId = :userId
               and e.requestId = :requestId
            """)
    Optional<EssayRecord> findByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") String requestId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            delete from EssayRecord e
             where e.id = :id
               and e.userId = :userId
               and e.createTime >= :accountCreatedAt
            """)
    int deleteOwnedById(
            @Param("id") Long id,
            @Param("userId") Long userId,
            @Param("accountCreatedAt") LocalDateTime accountCreatedAt
    );

    long countByUserId(Long userId);
}
