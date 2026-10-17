package com.english.assistant.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateChatSessionRequest(
        @Pattern(regexp = "^(free|cet4|cet6)$", message = "不支持的对话模式")
        String mode,

        @Size(max = 80, message = "会话标题不能超过80个字符")
        String title
) {
}
