package com.english.assistant.controller;

import com.english.assistant.entity.User;
import com.english.assistant.exception.ApiException;
import com.english.assistant.repository.UserRepository;
import com.english.assistant.security.CurrentUserService;
import com.english.assistant.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final CurrentUserService current;

    public AuthController(UserRepository users, PasswordEncoder encoder, JwtService jwt, CurrentUserService current) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.current = current;
    }

    public record RegisterRequest(
            @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 40, message = "用户名长度需为 3-40 个字符") String username,
            @Email(message = "邮箱格式不正确") @NotBlank(message = "邮箱不能为空") @Size(max = 120, message = "邮箱过长") String email,
            @NotBlank(message = "密码不能为空") @Size(min = 6, max = 60, message = "密码长度需为 6-60 个字符") String password
    ) {}

    public record LoginRequest(
            @NotBlank(message = "用户名不能为空") @Size(max = 40, message = "用户名过长") String username,
            @NotBlank(message = "密码不能为空") @Size(max = 60, message = "密码过长") String password
    ) {}

    @PostMapping("/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();
        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "用户名已存在");
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "邮箱已注册");
        }
        User user = users.save(new User(username, email, encoder.encode(request.password())));
        return Map.of(
                "token", jwt.generate(user.getId(), user.getUsername()),
                "username", user.getUsername(),
                "userId", user.getId()
        );
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        User user = users.findByUsername(request.username().trim()).orElse(null);
        if (user == null || !encoder.matches(request.password(), user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "用户名或密码错误");
        }
        return Map.of(
                "token", jwt.generate(user.getId(), user.getUsername()),
                "username", user.getUsername(),
                "userId", user.getId()
        );
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        User user = current.get();
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail()
        );
    }
}
