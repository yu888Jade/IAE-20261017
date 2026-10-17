package com.english.assistant.service;

import com.english.assistant.dto.EssayCheckResponse;
import com.english.assistant.dto.EssayError;
import com.english.assistant.dto.EssayRequest;
import com.english.assistant.dto.EssayResult;
import com.english.assistant.entity.EssayRecord;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.EssayRecordRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.List;

@Service
public class EssayService {
    private final DeepSeekService ai;
    private final EssayRecordRepository repo;
    private final RateLimitService rate;
    private final EssayGenerationGuardService guard;
    private final ObjectMapper mapper = new ObjectMapper();

    public EssayService(
            DeepSeekService ai,
            EssayRecordRepository repo,
            RateLimitService rate,
            EssayGenerationGuardService guard
    ) {
        this.ai = ai;
        this.repo = repo;
        this.rate = rate;
        this.guard = guard;
    }

    public EssayCheckResponse check(Long userId, EssayRequest request) {
        String requestId = request.getRequestId().trim();
        EssayRecord existing = repo.findByUserIdAndRequestId(userId, requestId).orElse(null);
        if (existing != null) return toResponse(existing);

        EssayGenerationGuardService.LockHandle lock = guard.tryAcquire(userId);
        if (lock == null) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "ESSAY_GENERATION_IN_PROGRESS",
                    "当前已有作文正在批改，请等待完成后再试"
            );
        }

        try {
            existing = repo.findByUserIdAndRequestId(userId, requestId).orElse(null);
            if (existing != null) return toResponse(existing);

            if (!rate.allow(userId)) {
                throw new ApiException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "AI_RATE_LIMITED",
                        "AI 请求过于频繁，请一分钟后再试"
                );
            }

            String essay = request.getEssay().trim();
            String level = request.getLevel().trim().toLowerCase();
            validateEnglishWordCount(essay);

            String json;
            try {
                json = ai.checkEssay(essay, level, userId);
            } catch (DeepSeekService.DeepSeekHttpException e) {
                throw mapDeepSeekError(e);
            } catch (HttpTimeoutException e) {
                throw new ApiException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "AI_TIMEOUT",
                        "AI 批改超时，请稍后重试"
                );
            } catch (IllegalStateException e) {
                if (e.getMessage() != null && e.getMessage().contains("DEEPSEEK_API_KEY")) {
                    throw new ApiException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "AI_NOT_CONFIGURED",
                            "AI 服务尚未配置，请联系管理员"
                    );
                }
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "AI_INVALID_RESPONSE",
                        "AI 返回内容异常，请重新批改"
                );
            } catch (Exception e) {
                Throwable cause = e;
                while (cause != null) {
                    if (cause instanceof HttpTimeoutException) {
                        throw new ApiException(
                                HttpStatus.GATEWAY_TIMEOUT,
                                "AI_TIMEOUT",
                                "AI 批改超时，请稍后重试"
                        );
                    }
                    cause = cause.getCause();
                }
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "AI_SERVICE_ERROR",
                        "AI 服务暂时不可用，请稍后重试"
                );
            }

            EssayResult result = parseAndValidate(json);
            String normalizedJson;
            try {
                normalizedJson = mapper.writeValueAsString(result);
            } catch (Exception e) {
                throw new ApiException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "ESSAY_RESULT_SERIALIZE_FAILED",
                        "批改结果保存失败，请重新尝试"
                );
            }

            EssayRecord saved = repo.save(new EssayRecord(
                    userId,
                    requestId,
                    level,
                    essay,
                    normalizedJson,
                    result.getScore()
            ));
            return new EssayCheckResponse(saved.getId(), requestId, result);
        } finally {
            guard.release(lock);
        }
    }

    private EssayCheckResponse toResponse(EssayRecord record) {
        return new EssayCheckResponse(record.getId(), record.getRequestId(), parseStored(record.getResultJson()));
    }

    private EssayResult parseStored(String json) {
        try {
            return mapper.readValue(json, EssayResult.class);
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "ESSAY_RECORD_CORRUPTED",
                    "历史批改记录格式异常"
            );
        }
    }

    private EssayResult parseAndValidate(String json) {
        try {
            JsonNode root = mapper.readTree(json);
            if (root == null || !root.isObject()) throw new IllegalArgumentException();

            JsonNode scoreNode = root.get("score");
            if (scoreNode == null || !scoreNode.isNumber()) throw new IllegalArgumentException();
            int score = scoreNode.asInt();
            if (score < 0 || score > 100) throw new IllegalArgumentException();

            EssayResult result = new EssayResult();
            result.setScore(score);
            result.setStrengths(readStringArray(root.get("strengths"), 20, 1000));
            result.setSuggestions(readStringArray(root.get("suggestions"), 20, 1000));

            JsonNode errorsNode = root.get("errors");
            if (errorsNode == null || !errorsNode.isArray() || errorsNode.size() > 50) {
                throw new IllegalArgumentException();
            }
            List<EssayError> errors = new ArrayList<>();
            for (JsonNode node : errorsNode) {
                if (!node.isObject()) throw new IllegalArgumentException();
                String original = requiredText(node, "original", 3000);
                String correct = requiredText(node, "correct", 3000);
                String problem = requiredText(node, "problem", 5000);
                EssayError error = new EssayError();
                error.setOriginal(original);
                error.setCorrect(correct);
                error.setProblem(problem);
                errors.add(error);
            }
            result.setErrors(errors);
            result.setImprovedEssay(requiredText(root, "improvedEssay", 20000));
            return result;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "AI_INVALID_RESPONSE",
                    "AI 返回的批改结果格式异常，请重新批改"
            );
        }
    }

    private List<String> readStringArray(JsonNode node, int maxItems, int maxLength) {
        if (node == null || !node.isArray() || node.size() > maxItems) throw new IllegalArgumentException();
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) throw new IllegalArgumentException();
            String value = item.asText().trim();
            if (value.isEmpty() || value.length() > maxLength) throw new IllegalArgumentException();
            values.add(value);
        }
        return values;
    }

    private String requiredText(JsonNode node, String field, int maxLength) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException();
        String text = value.asText().trim();
        if (text.isEmpty() || text.length() > maxLength) throw new IllegalArgumentException();
        return text;
    }

    private void validateEnglishWordCount(String essay) {
        long words = java.util.Arrays.stream(essay.split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .count();
        if (words < 20) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ESSAY_TOO_SHORT",
                    "作文至少需要20个英文单词"
            );
        }
    }

    private ApiException mapDeepSeekError(DeepSeekService.DeepSeekHttpException e) {
        if (e.getStatusCode() == 429) {
            return new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "AI_PROVIDER_RATE_LIMITED",
                    "AI 服务当前请求较多，请稍后重试"
            );
        }
        if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
            return new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "AI_AUTH_FAILED",
                    "AI 服务认证失败，请联系管理员"
            );
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "AI_SERVICE_ERROR",
                "AI 服务暂时不可用，请稍后重试"
        );
    }
}
