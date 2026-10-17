package com.english.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ChatMessage {
    @NotBlank(message = "消息角色不能为空")
    @Pattern(regexp = "^(user|assistant)$", message = "消息角色不合法")
    private String role;

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "单条历史消息不能超过4000个字符")
    private String content;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
}
