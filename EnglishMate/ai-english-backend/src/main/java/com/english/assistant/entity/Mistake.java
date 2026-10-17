package com.english.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "mistake_book",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_mistake_user_hash",
                columnNames = {"user_id", "content_hash"}
        ),
        indexes = {
                @Index(name = "idx_mistake_user", columnList = "user_id"),
                @Index(name = "idx_mistake_essay_record", columnList = "user_id,essay_record_id")
        }
)
public class Mistake {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "essay_record_id")
    private Long essayRecordId;

    @Lob
    private String originalText;

    @Lob
    private String correctedText;

    @Lob
    private String explanation;

    @Column(length = 30)
    private String source;

    @Column(name = "content_hash", length = 64)
    private String contentHash;

    private boolean mastered;
    private LocalDateTime createTime;

    public Mistake() {}

    public Mistake(
            Long userId,
            Long essayRecordId,
            String originalText,
            String correctedText,
            String explanation,
            String source,
            String contentHash
    ) {
        this.userId = userId;
        this.essayRecordId = essayRecordId;
        this.originalText = originalText;
        this.correctedText = correctedText;
        this.explanation = explanation;
        this.source = source;
        this.contentHash = contentHash;
        this.createTime = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getEssayRecordId() { return essayRecordId; }
    public String getOriginalText() { return originalText; }
    public void setOriginalText(String value) { originalText = value; }
    public String getCorrectedText() { return correctedText; }
    public void setCorrectedText(String value) { correctedText = value; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String value) { explanation = value; }
    public String getSource() { return source; }
    public void setSource(String value) { source = value; }
    public String getContentHash() { return contentHash; }
    public boolean isMastered() { return mastered; }
    public void setMastered(boolean value) { mastered = value; }
    public LocalDateTime getCreateTime() { return createTime; }
}
