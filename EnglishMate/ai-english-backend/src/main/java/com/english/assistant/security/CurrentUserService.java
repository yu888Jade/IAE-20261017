package com.english.assistant.security;
import com.english.assistant.entity.User; import com.english.assistant.repository.UserRepository; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Service;
@Service public class CurrentUserService {private final UserRepository repo; public CurrentUserService(UserRepository r){repo=r;} public User get(){String name=SecurityContextHolder.getContext().getAuthentication().getName(); return repo.findByUsername(name).orElseThrow();} public Long id(){return get().getId();}}
