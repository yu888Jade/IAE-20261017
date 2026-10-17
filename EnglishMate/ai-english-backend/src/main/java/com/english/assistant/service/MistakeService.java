package com.english.assistant.service;

import com.english.assistant.dto.MistakeRequest;
import com.english.assistant.entity.Mistake;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.EssayRecordRepository;
import com.english.assistant.repository.MistakeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Service
public class MistakeService {
    private final MistakeRepository repo;
    private final EssayRecordRepository essays;

    public MistakeService(MistakeRepository repo, EssayRecordRepository essays) {
        this.repo = repo;
        this.essays = essays;
    }

    public Mistake add(User user, MistakeRequest request) {
        Long userId = user.getId();
        java.time.LocalDateTime accountCreatedAt = user.getCreateTime() == null
                ? java.time.LocalDateTime.of(1970, 1, 1, 0, 0)
                : user.getCreateTime();
        essays.findOwnedById(request.getEssayRecordId(), userId, accountCreatedAt)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ESSAY_RECORD_NOT_FOUND",
                        "对应的作文记录不存在或不属于当前用户"
                ));

        String original = request.getOriginalText().trim();
        String corrected = request.getCorrectedText().trim();
        String explanation = request.getExplanation().trim();
        String source = request.getSource().trim().toLowerCase();
        String hash = hash(source + "\n" + original + "\n" + corrected + "\n" + explanation);

        Mistake existing = repo.findOwnedByContentHash(userId, hash, accountCreatedAt).orElse(null);
        if (existing != null) return existing;

        try {
            return repo.save(new Mistake(
                    userId,
                    request.getEssayRecordId(),
                    original,
                    corrected,
                    explanation,
                    source,
                    hash
            ));
        } catch (DataIntegrityViolationException e) {
            return repo.findOwnedByContentHash(userId, hash, accountCreatedAt).orElseThrow(() -> e);
        }
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("无法生成错题指纹", e);
        }
    }
}
