package com.english.assistant.dto;

import java.time.LocalDateTime;

public class ApiErrorResponse {

    private int status;

    private String message;

    private LocalDateTime timestamp;

    public ApiErrorResponse() {
    }

    public ApiErrorResponse(
            int status,
            String message
    ) {

        this.status = status;
        this.message = message;
        this.timestamp =
                LocalDateTime.now();
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(
            int status
    ) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(
            String message
    ) {
        this.message = message;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(
            LocalDateTime timestamp
    ) {
        this.timestamp = timestamp;
    }
}