package com.english.assistant.dto.profile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserProfileRequest(
        @NotBlank(message = "学习目标不能为空")
        @Pattern(regexp = "CET-4|CET-6|口语提升", message = "学习目标不合法")
        String goal,

        @NotBlank(message = "当前英语水平不能为空")
        @Size(max = 100, message = "当前英语水平不能超过100个字符")
        String level,

        @NotBlank(message = "每日学习时间不能为空")
        @Size(max = 50, message = "每日学习时间不能超过50个字符")
        String studyTime,

        @NotBlank(message = "薄弱方向不能为空")
        @Size(max = 200, message = "薄弱方向不能超过200个字符")
        String weakness
) {}
