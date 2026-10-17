package com.english.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "essay_record",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_essay_user_request",
                columnNames = {"user_id", "request_id"}
        )
)
public class EssayRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_id", length = 80)
    private String requestId;

    @Column(length = 20)
    private String level;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String originalContent;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String resultJson;

    private Integer score;
    private LocalDateTime createTime;

    public EssayRecord() {}

    public EssayRecord(Long userId, String requestId, String level, String originalContent, String resultJson, Integer score) {
        this.userId = userId;
        this.requestId = requestId;
        this.level = level;
        this.originalContent = originalContent;
        this.resultJson = resultJson;
        this.score = score;
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getRequestId() { return requestId; }
    public String getLevel() { return level; }
    public String getOriginalContent() { return originalContent; }
    public String getResultJson() { return resultJson; }
    public Integer getScore() { return score; }
    public LocalDateTime getCreateTime() { return createTime; }
}
