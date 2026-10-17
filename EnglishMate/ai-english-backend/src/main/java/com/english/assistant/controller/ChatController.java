package com.english.assistant.controller;

import com.english.assistant.dto.ChatMessage;
import com.english.assistant.dto.ChatSendRequest;
import com.english.assistant.dto.CreateChatSessionRequest;
import com.english.assistant.entity.ChatHistory;
import com.english.assistant.entity.ChatSession;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.ChatHistoryRepository;
import com.english.assistant.repository.ChatSessionRepository;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.ChatGenerationService;
import com.english.assistant.service.DeepSeekService;
import com.english.assistant.service.RateLimitService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final Set<String> MODES = Set.of("free", "cet4", "cet6");
    private static final int MAX_CONTEXT_MESSAGES = 40;
    private static final int MAX_CONTEXT_CHARS = 20_000;

    private final ChatSessionRepository sessions;
    private final ChatHistoryRepository messages;
    private final CurrentUserService current;
    private final DeepSeekService ai;
    private final RateLimitService rate;
    private final ChatGenerationService generations;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatController(
            ChatSessionRepository sessions,
            ChatHistoryRepository messages,
            CurrentUserService current,
            DeepSeekService ai,
            RateLimitService rate,
            ChatGenerationService generations) {
        this.sessions = sessions;
        this.messages = messages;
        this.current = current;
        this.ai = ai;
        this.rate = rate;
        this.generations = generations;
    }

    @GetMapping("/sessions")
    public List<ChatSession> list() {
        User user = current.get();
        return sessions.findOwnedSessions(user.getId(), accountCreatedAt(user));
    }

    @PostMapping("/sessions")
    public ChatSession create(@Valid @RequestBody CreateChatSessionRequest request) {
        String mode = normalize(request.mode());
        String title = request.title() == null || request.title().isBlank()
                ? "新对话"
                : request.title().trim();
        return sessions.save(new ChatSession(UUID.randomUUID().toString(), current.id(), title, mode));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        User user = current.get();
        Long uid = user.getId();
        LocalDateTime createdAt = accountCreatedAt(user);
        ChatSession session = sessions.findOwnedById(id, uid, createdAt)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "会话不存在"));

        generations.cancelAndRun(id, () -> {
            messages.deleteOwnedSessionHistory(uid, id);
            sessions.deleteOwnedById(id, uid, createdAt);
        });
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions/{id}/messages")
    public List<ChatHistory> history(@PathVariable String id) {
        User user = current.get();
        requireSession(id, user.getId(), accountCreatedAt(user));
        return messages.findOwnedHistory(user.getId(), id, accountCreatedAt(user));
    }

    @PostMapping(value = "/sessions/{id}/stream", produces = "text/event-stream;charset=UTF-8")
    public StreamingResponseBody stream(
            @PathVariable String id,
            @Valid @RequestBody ChatSendRequest request,
            HttpServletResponse servletResponse) {
        long receivedAt = System.currentTimeMillis();
        log.info("[AI-PERF] request received sessionId={}", id);

        User user = current.get();
        Long uid = user.getId();
        LocalDateTime createdAt = accountCreatedAt(user);
        ChatSession session = requireSession(id, uid, createdAt);
        String requestId = request.requestId();

        ChatHistory completed = messages
                .findFirstOwnedRequestMessage(uid, id, requestId, "assistant", createdAt)
                .orElse(null);
        if (completed != null) {
            log.info("[AI-PERF] idempotent replay for requestId={}", requestId);
            return replayBody(rawStream(servletResponse), completed.getContent());
        }

        ChatGenerationService.GenerationState state = generations.begin(id, requestId);
        final List<ChatMessage> databaseContext;
        try {
            completed = messages
                    .findFirstOwnedRequestMessage(uid, id, requestId, "assistant", createdAt)
                    .orElse(null);
            if (completed != null) {
                generations.finish(id, state);
                return replayBody(rawStream(servletResponse), completed.getContent());
            }

            long mark = System.currentTimeMillis();
            if (!rate.allow(uid)) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AI_RATE_LIMITED",
                        "AI 请求过于频繁，请一分钟后再试");
            }
            long rateLimitMs = System.currentTimeMillis() - mark;

            String text = request.message().trim();
            mark = System.currentTimeMillis();
            boolean hasUserMessage = messages
                    .findFirstOwnedRequestMessage(uid, id, requestId, "user", createdAt)
                    .isPresent();
            if (!hasUserMessage) {
                messages.save(new ChatHistory(uid, "user", text, session.getMode(), id, requestId));
            }
            long persistUserMs = System.currentTimeMillis() - mark;

            String title = session.getTitle();
            if ("新对话".equals(title)) {
                String normalizedTitle = text.replaceAll("\\s+", " ");
                title = normalizedTitle.length() > 28
                        ? normalizedTitle.substring(0, 28) + "…"
                        : normalizedTitle;
                session.setTitle(title);
            }
            LocalDateTime updateTime = LocalDateTime.now();
            session.setUpdateTime(updateTime);
            int updated = sessions.updateOwnedSessionMeta(id, uid, createdAt, title, updateTime);
            if (updated == 0) {
                throw new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "会话不存在");
            }

            mark = System.currentTimeMillis();
            databaseContext = loadDatabaseContext(uid, id, createdAt);
            long loadContextMs = System.currentTimeMillis() - mark;

            long preprocessingMs = System.currentTimeMillis() - receivedAt;
            log.info(
                    "[AI-PERF] request preprocessing: {} ms [rateLimit={} ms, persistUser={} ms, loadContext={} ms, contextMessages={}]",
                    preprocessingMs, rateLimitMs, persistUserMs, loadContextMs, databaseContext.size());
        } catch (RuntimeException e) {
            generations.finish(id, state);
            throw e;
        }

        servletResponse.setHeader("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate");
        servletResponse.setHeader("X-Accel-Buffering", "no");

        return outputStream -> {
            // Spring 7 传给 StreamingResponseBody 的是 NonFlushingOutputStream（flush 被吞，数据攒满
            // 8KB 才发），
            // 必须直接使用原生响应流才能保证每个 token 实时下发。
            OutputStream raw = rawStream(servletResponse);
            Writer writer = new BufferedWriter(new OutputStreamWriter(raw, StandardCharsets.UTF_8));
            try {
                String answer = ai.streamChat(databaseContext, session.getMode(), uid, chunk -> {
                    if (state.isCancelled())
                        throw new ChatStreamCancelledException();
                    try {
                        writeSseEvent(writer, "chunk", chunk);
                        writer.flush();
                        raw.flush();
                    } catch (IOException e) {
                        throw new ChatStreamCancelledException();
                    }
                });

                if (answer == null || answer.isBlank()) {
                    throw new IllegalStateException("AI返回内容为空");
                }

                long persistStart = System.currentTimeMillis();
                boolean saved = generations.runIfActive(id, state, () -> {
                    if (sessions.findOwnedById(id, uid, createdAt).isEmpty())
                        return;
                    messages.save(new ChatHistory(
                            uid,
                            "assistant",
                            answer,
                            session.getMode(),
                            id,
                            requestId));
                });
                long persistMs = System.currentTimeMillis() - persistStart;

                if (saved && sessions.findOwnedById(id, uid, createdAt).isPresent()) {
                    writeSseEvent(writer, "done", "[DONE]");
                    writer.flush();
                    raw.flush();
                }
                log.info("[AI-PERF] total request: {} ms [persistAssistant={} ms, answerChars={}]",
                        System.currentTimeMillis() - receivedAt, persistMs, answer.length());
            } catch (ChatStreamCancelledException e) {
                log.info("[AI-PERF] stream stopped/cancelled after {} ms", System.currentTimeMillis() - receivedAt);
            } catch (Exception e) {
                log.warn("[AI-PERF] stream failed after {} ms: {}",
                        System.currentTimeMillis() - receivedAt, rootCause(e).toString());
                writeStreamError(writer, e);
            } finally {
                generations.finish(id, state);
            }
        };
    }

    private List<ChatMessage> loadDatabaseContext(Long uid, String sessionId, LocalDateTime accountCreatedAt) {
        List<ChatHistory> recent = messages.findOwnedRecentHistory(
                uid,
                sessionId,
                accountCreatedAt,
                PageRequest.of(0, MAX_CONTEXT_MESSAGES));
        Collections.reverse(recent);

        List<ChatMessage> context = new ArrayList<>();
        int totalChars = 0;
        for (ChatHistory message : recent) {
            if (!("user".equals(message.getRole()) || "assistant".equals(message.getRole())))
                continue;
            if (message.getContent() == null || message.getContent().isBlank())
                continue;
            totalChars += message.getContent().length();
            if (totalChars > MAX_CONTEXT_CHARS) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "CHAT_CONTEXT_TOO_LARGE",
                        "当前对话上下文过长，请新建会话后继续");
            }
            context.add(new ChatMessage(message.getRole(), message.getContent()));
        }
        return context;
    }

    /**
     * 手写 SSE 帧，保证 Writer 关闭时容器正确输出 chunked 结束帧（0\r\n\r\n）。
     */
    private void writeSseEvent(Writer writer, String event, String data) throws IOException {
        writer.write("event:");
        writer.write(event);
        writer.write('\n');
        String[] lines = data.split("\n", -1);
        for (String line : lines) {
            writer.write("data:");
            writer.write(line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
            writer.write('\n');
        }
        writer.write('\n');
    }

    private static OutputStream rawStream(HttpServletResponse response) {
        try {
            return response.getOutputStream();
        } catch (IOException e) {
            throw new IllegalStateException("无法获取响应输出流", e);
        }
    }

    private StreamingResponseBody replayBody(OutputStream raw, String content) {
        return outputStream -> {
            Writer writer = new BufferedWriter(new OutputStreamWriter(raw, StandardCharsets.UTF_8));
            writeSseEvent(writer, "chunk", content);
            writeSseEvent(writer, "done", "[DONE]");
            writer.flush();
            raw.flush();
        };
    }

    private void writeStreamError(Writer writer, Exception error) {
        String code = "AI_SERVICE_ERROR";
        String message = "AI 服务暂时不可用，请稍后重试";

        Throwable root = rootCause(error);
        if (root instanceof HttpTimeoutException) {
            code = "AI_TIMEOUT";
            message = "AI 响应超时，请重试";
        } else if (root instanceof DeepSeekService.DeepSeekHttpException deepSeekError) {
            if (deepSeekError.getStatusCode() == 429) {
                code = "AI_PROVIDER_RATE_LIMITED";
                message = "AI 服务当前繁忙，请稍后重试";
            } else if (deepSeekError.getStatusCode() == 401 || deepSeekError.getStatusCode() == 403) {
                code = "AI_CONFIGURATION_ERROR";
                message = "AI 服务配置异常，请联系管理员";
            }
        } else if (root instanceof IllegalStateException && root.getMessage() != null) {
            message = root.getMessage();
        }

        try {
            String json = mapper.writeValueAsString(new StreamError(code, message));
            writeSseEvent(writer, "error", json);
            writer.flush();
        } catch (Exception sendError) {
            // 客户端已断开时无法再写入错误帧，忽略即可。
        }
    }

    private Throwable rootCause(Throwable error) {
        Throwable currentError = error;
        while (currentError.getCause() != null && currentError.getCause() != currentError) {
            currentError = currentError.getCause();
        }
        return currentError;
    }

    private ChatSession requireSession(String id, Long uid, LocalDateTime accountCreatedAt) {
        return sessions.findOwnedById(id, uid, accountCreatedAt)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "CHAT_SESSION_NOT_FOUND", "会话不存在"));
    }

    private LocalDateTime accountCreatedAt(User user) {
        return user.getCreateTime() == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : user.getCreateTime();
    }

    private String normalize(String mode) {
        String value = mode == null ? "free" : mode.trim().toLowerCase();
        if (!MODES.contains(value)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CHAT_MODE_INVALID", "不支持的对话模式");
        }
        return value;
    }

    private record StreamError(String code, String message) {
    }

    private static class ChatStreamCancelledException extends RuntimeException {
    }
}
