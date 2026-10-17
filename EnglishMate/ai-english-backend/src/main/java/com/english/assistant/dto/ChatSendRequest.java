package com.english.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChatSendRequest(
        @NotBlank(message = "消息不能为空")
        @Size(max = 4000, message = "单条消息不能超过4000个字符")
        String message,

        @NotBlank(message = "requestId不能为空")
        @Pattern(
                regexp = "^[0-9a-fA-F-]{36}$",
                message = "requestId格式不正确"
        )
        String requestId
) {
}
