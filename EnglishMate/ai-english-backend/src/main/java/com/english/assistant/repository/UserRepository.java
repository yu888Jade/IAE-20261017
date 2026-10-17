package com.english.assistant.repository; import com.english.assistant.entity.User; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional;
public interface UserRepository extends JpaRepository<User,Long>{Optional<User> findByUsername(String username); Optional<User> findByEmail(String email); boolean existsByUsername(String u); boolean existsByEmail(String e);}
