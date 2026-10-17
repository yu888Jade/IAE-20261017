package com.english.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_plan")
public class StudyPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    @Column(name = "profile_fingerprint", length = 64)
    private String profileFingerprint;

    @Column(name = "plan_version", length = 36)
    private String planVersion;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public StudyPlan() {}

    public StudyPlan(Long userId, String content, String profileFingerprint, String planVersion) {
        this.userId = userId;
        this.content = content;
        this.profileFingerprint = profileFingerprint;
        this.planVersion = planVersion;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) createTime = now;
        updateTime = now;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getProfileFingerprint() { return profileFingerprint; }
    public void setProfileFingerprint(String profileFingerprint) { this.profileFingerprint = profileFingerprint; }
    public String getPlanVersion() { return planVersion; }
    public void setPlanVersion(String planVersion) { this.planVersion = planVersion; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
