package com.english.assistant.controller;

import com.english.assistant.dto.EssayCheckResponse;
import com.english.assistant.dto.EssayRequest;
import com.english.assistant.entity.EssayRecord;
import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.EssayRecordRepository;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.EssayService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/essay")
public class EssayController {
    private final CurrentUserService current;
    private final EssayRecordRepository repo;
    private final EssayService essays;

    public EssayController(CurrentUserService current, EssayRecordRepository repo, EssayService essays) {
        this.current = current;
        this.repo = repo;
        this.essays = essays;
    }

    @PostMapping("/check")
    public EssayCheckResponse check(@Valid @RequestBody EssayRequest request) {
        return essays.check(current.id(), request);
    }

    @GetMapping("/records")
    public List<EssayRecord> records(@RequestParam Long userId) {
        User user = current.get();
        assertSameUser(userId, user.getId());
        return repo.findValidOwnedRecords(user.getId(), accountCreatedAt(user));
    }

    @GetMapping("/records/{id}")
    public EssayRecord record(@PathVariable Long id) {
        User user = current.get();
        Long uid = user.getId();
        return repo.findOwnedById(id, uid, accountCreatedAt(user))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ESSAY_RECORD_NOT_FOUND", "作文记录不存在"));
    }

    @DeleteMapping("/records/{id}")
    public void delete(@PathVariable Long id) {
        User user = current.get();
        Long uid = user.getId();
        int deleted = repo.deleteOwnedById(id, uid, accountCreatedAt(user));
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ESSAY_RECORD_NOT_FOUND", "作文记录不存在");
        }
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
