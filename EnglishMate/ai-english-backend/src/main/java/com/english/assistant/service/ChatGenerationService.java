package com.english.assistant.service;

import com.english.assistant.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatGenerationService {
    private final ConcurrentHashMap<String, GenerationState> generations = new ConcurrentHashMap<>();

    public GenerationState begin(String sessionId, String requestId) {
        GenerationState next = new GenerationState(requestId);
        GenerationState existing = generations.putIfAbsent(sessionId, next);
        if (existing != null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CHAT_GENERATION_IN_PROGRESS",
                    "当前会话正在生成回复，请等待完成后再发送"
            );
        }
        return next;
    }

    public boolean runIfActive(String sessionId, GenerationState state, Runnable action) {
        synchronized (state) {
            if (state.isCancelled() || generations.get(sessionId) != state) return false;
            action.run();
            return true;
        }
    }

    public void cancelAndRun(String sessionId, Runnable action) {
        GenerationState state = generations.get(sessionId);
        if (state == null) {
            action.run();
            return;
        }
        synchronized (state) {
            state.cancel();
            action.run();
        }
    }

    public void finish(String sessionId, GenerationState state) {
        generations.remove(sessionId, state);
    }

    public static final class GenerationState {
        private final String requestId;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        private GenerationState(String requestId) {
            this.requestId = requestId;
        }

        public String getRequestId() { return requestId; }
        public void cancel() { cancelled.set(true); }
        public boolean isCancelled() { return cancelled.get(); }
    }
}
