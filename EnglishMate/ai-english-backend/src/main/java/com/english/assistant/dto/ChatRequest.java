package com.english.assistant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public class ChatRequest {

    @NotEmpty(
            message = "聊天消息不能为空"
    )
    @Valid
    private List<ChatMessage> messages;

    @NotBlank(
            message = "聊天模式不能为空"
    )
    private String mode;

    public ChatRequest() {
    }

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(
            List<ChatMessage> messages
    ) {
        this.messages = messages;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(
            String mode
    ) {
        this.mode = mode;
    }
}