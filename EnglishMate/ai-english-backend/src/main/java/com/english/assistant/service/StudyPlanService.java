package com.english.assistant.service;

import com.english.assistant.entity.StudyPlan;
import com.english.assistant.entity.UserProfile;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.StudyPlanRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpTimeoutException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class StudyPlanService {
    private final DeepSeekService ai;
    private final StudyPlanRepository repo;
    private final UserProfileService profiles;
    private final RateLimitService rateLimit;
    private final PlanGenerationGuardService guard;
    private final ObjectMapper mapper = new ObjectMapper();

    public StudyPlanService(
            DeepSeekService ai,
            StudyPlanRepository repo,
            UserProfileService profiles,
            RateLimitService rateLimit,
            PlanGenerationGuardService guard
    ) {
        this.ai = ai;
        this.repo = repo;
        this.profiles = profiles;
        this.rateLimit = rateLimit;
        this.guard = guard;
    }

    public PlanResponse getPlan(Long userId) {
        UserProfile profile = requireCompleteProfile(userId);
        String fingerprint = profiles.profileFingerprint(profile);
        StudyPlan plan = repo.findByUserId(userId).orElse(null);

        if (plan == null) {
            return new PlanResponse("PLAN_NOT_GENERATED", null, "学习档案已完成，可以生成你的7天学习计划。", null);
        }

        if (plan.getProfileFingerprint() == null || !fingerprint.equals(plan.getProfileFingerprint())
                || plan.getPlanVersion() == null || plan.getPlanVersion().isBlank()) {
            return new PlanResponse("PLAN_STALE", null, "学习档案已更新，请重新生成学习计划。", null);
        }

        JsonNode content = parseAndValidate(plan.getContent());
        return new PlanResponse("PLAN_READY", content, null, plan.getPlanVersion());
    }

    public PlanResponse regenerate(Long userId) {
        UserProfile profileBefore = requireCompleteProfile(userId);
        String fingerprintBefore = profiles.profileFingerprint(profileBefore);

        PlanGenerationGuardService.LockHandle lock = guard.tryAcquire(userId);
        if (lock == null) {
            throw new ApiException(HttpStatus.CONFLICT, "PLAN_GENERATION_IN_PROGRESS", "学习计划正在生成，请勿重复提交。");
        }

        try {
            if (!rateLimit.allow(userId)) {
                throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED", "AI 请求过于频繁，请一分钟后再试。");
            }

            String raw;
            try {
                raw = ai.generateStudyPlan(userId);
            } catch (HttpTimeoutException e) {
                throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "AI 生成超时，请稍后重试。");
            } catch (DeepSeekService.DeepSeekHttpException e) {
                if (e.getStatusCode() == 429) {
                    throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_RATE_LIMITED", "DeepSeek 当前请求过多，请稍后重试。");
                }
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_ERROR", "AI 服务暂时不可用，请稍后重试。");
            } catch (IllegalStateException e) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_CONFIG_OR_RESPONSE_ERROR", "AI 服务配置或响应异常，请联系管理员检查配置。");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_INTERRUPTED", "AI 生成被中断，请稍后重试。");
            } catch (Exception e) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_SERVICE_ERROR", "AI 服务暂时不可用，请稍后重试。");
            }

            JsonNode normalized = parseAndValidate(raw);

            UserProfile profileAfter = requireCompleteProfile(userId);
            String fingerprintAfter = profiles.profileFingerprint(profileAfter);
            if (!fingerprintBefore.equals(fingerprintAfter)) {
                throw new ApiException(HttpStatus.CONFLICT, "PROFILE_CHANGED_DURING_GENERATION", "生成期间学习档案发生变化，请重新生成计划。");
            }

            String canonicalJson = mapper.writeValueAsString(normalized);
            String planVersion = UUID.randomUUID().toString();
            savePlan(userId, canonicalJson, fingerprintAfter, planVersion);
            return new PlanResponse("PLAN_READY", normalized, null, planVersion);
        } finally {
            guard.release(lock);
        }
    }

    private UserProfile requireCompleteProfile(Long userId) {
        UserProfile profile = profiles.getProfileByUserId(userId);
        if (profile == null) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PROFILE_REQUIRED", "请先完善学习档案，再生成学习计划。");
        }
        if (!profiles.isComplete(profile)) {
            throw new ApiException(HttpStatus.PRECONDITION_REQUIRED, "PROFILE_INCOMPLETE", "学习档案不完整，请先补充完整后再生成计划。");
        }
        return profile;
    }

    @Transactional
    protected void savePlan(Long userId, String content, String fingerprint, String planVersion) {
        StudyPlan plan = repo.findByUserId(userId)
                .orElseGet(() -> new StudyPlan(userId, content, fingerprint, planVersion));
        plan.setContent(content);
        plan.setProfileFingerprint(fingerprint);
        plan.setPlanVersion(planVersion);
        repo.save(plan);
    }

    private JsonNode parseAndValidate(String raw) {
        final JsonNode root;
        try {
            root = mapper.readTree(raw);
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_JSON", "AI 返回的学习计划格式异常，请重新生成。");
        }

        if (root == null || !root.isObject()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_PLAN", "AI 返回的学习计划结构异常，请重新生成。");
        }
        if (!hasText(root, "profileSummary") || !hasText(root, "focus")) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_PLAN", "AI 返回的学习计划缺少必要字段，请重新生成。");
        }

        JsonNode days = root.path("days");
        if (!days.isArray() || days.size() != 7) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_PLAN", "AI 返回的学习计划必须包含完整7天内容，请重新生成。");
        }

        Set<Integer> seenDays = new HashSet<>();
        for (JsonNode day : days) {
            int dayNumber = day.path("day").asInt(-1);
            if (dayNumber < 1 || dayNumber > 7 || !seenDays.add(dayNumber)
                    || !hasText(day, "title") || !hasText(day, "goal")
                    || !hasText(day, "duration") || !hasText(day, "review")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_PLAN", "AI 返回的每日计划结构异常，请重新生成。");
            }
            JsonNode tasks = day.path("tasks");
            if (!tasks.isArray() || tasks.size() == 0) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_PLAN", "AI 返回的每日任务不能为空，请重新生成。");
            }
            for (JsonNode task : tasks) {
                if (!task.isTextual() || task.asText().isBlank()) {
                    throw new ApiException(HttpStatus.BAD_GATEWAY, "AI_INVALID_PLAN", "AI 返回的每日任务格式异常，请重新生成。");
                }
            }
        }
        return root;
    }

    private boolean hasText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.asText().isBlank();
    }

    public record PlanResponse(
            String status,
            JsonNode plan,
            String message,
            String planVersion
    ) {}
}
