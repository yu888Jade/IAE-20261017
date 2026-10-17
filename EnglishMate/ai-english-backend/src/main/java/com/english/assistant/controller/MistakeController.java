package com.english.assistant.controller;

import com.english.assistant.dto.MistakeRequest;
import com.english.assistant.entity.Mistake;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.MistakeRepository;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.MistakeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/mistakes")
public class MistakeController {
    private final MistakeRepository repo;
    private final CurrentUserService current;
    private final MistakeService mistakes;

    public MistakeController(MistakeRepository repo, CurrentUserService current, MistakeService mistakes) {
        this.repo = repo;
        this.current = current;
        this.mistakes = mistakes;
    }

    @GetMapping
    public List<Mistake> list(@RequestParam Long userId) {
        User user = current.get();
        assertSameUser(userId, user.getId());
        return repo.findValidOwnedMistakes(user.getId(), accountCreatedAt(user));
    }

    @PostMapping
    public Mistake add(@Valid @RequestBody MistakeRequest request) {
        return mistakes.add(current.get(), request);
    }

    @PutMapping("/{id}/mastered")
    public Mistake mastered(@PathVariable Long id, @RequestParam boolean value) {
        User user = current.get();
        Long uid = user.getId();
        LocalDateTime createdAt = accountCreatedAt(user);
        int updated = repo.updateOwnedMastered(id, uid, createdAt, value);
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MISTAKE_NOT_FOUND", "错题不存在");
        }
        return repo.findOwnedById(id, uid, createdAt)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MISTAKE_NOT_FOUND", "错题不存在"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        User user = current.get();
        Long uid = user.getId();
        int deleted = repo.deleteOwnedById(id, uid, accountCreatedAt(user));
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "MISTAKE_NOT_FOUND", "错题不存在或不属于当前用户");
        }
        return ResponseEntity.noContent().build();
    }

    private void assertSameUser(Long requestedUserId, Long currentUserId) {
        if (requestedUserId == null || !requestedUserId.equals(currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_SCOPE_FORBIDDEN", "无权读取其他用户的数据");
        }
    }

    private LocalDateTime accountCreatedAt(User user) {
        return user.getCreateTime() == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : user.getCreateTime();
    }
}
