package com.english.assistant.dto;

public record EssayCheckResponse(
        Long recordId,
        String requestId,
        EssayResult result
) {}
