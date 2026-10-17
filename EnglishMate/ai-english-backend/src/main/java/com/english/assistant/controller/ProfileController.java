package com.english.assistant.controller;

import com.english.assistant.dto.profile.UserProfileRequest;
import com.english.assistant.entity.UserProfile;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {
    private final UserProfileService service;
    private final CurrentUserService current;

    public ProfileController(UserProfileService service, CurrentUserService current) {
        this.service = service;
        this.current = current;
    }

    @GetMapping
    public ResponseEntity<UserProfile> get() {
        return ResponseEntity.ok(service.getProfileByUserId(current.id()));
    }

    @PostMapping
    public ResponseEntity<UserProfile> save(@Valid @RequestBody UserProfileRequest request) {
        return ResponseEntity.ok(service.saveOrUpdateProfile(current.id(), request));
    }
}
