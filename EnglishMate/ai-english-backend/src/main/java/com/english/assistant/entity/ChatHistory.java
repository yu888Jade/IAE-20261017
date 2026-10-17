package com.english.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "chat_history",
        indexes = {
                @Index(name = "idx_chat_history_user_session", columnList = "user_id,session_id"),
                @Index(name = "idx_chat_history_request", columnList = "user_id,session_id,request_id")
        }
)
public class ChatHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "mode", nullable = false, length = 20)
    private String mode;

    @Column(name = "session_id", nullable = false, length = 80)
    private String sessionId;

    @Column(name = "request_id", length = 36)
    private String requestId;

    private LocalDateTime createTime;

    public ChatHistory() {
    }

    public ChatHistory(Long userId, String role, String content, String mode, String sessionId) {
        this(userId, role, content, mode, sessionId, null);
    }

    public ChatHistory(Long userId, String role, String content, String mode, String sessionId, String requestId) {
        this.userId = userId;
        this.role = role;
        this.content = content;
        this.mode = mode;
        this.sessionId = sessionId;
        this.requestId = requestId;
        this.createTime = LocalDateTime.now();
    }

    @PrePersist
    public void prePersist() {
        if (createTime == null) createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
