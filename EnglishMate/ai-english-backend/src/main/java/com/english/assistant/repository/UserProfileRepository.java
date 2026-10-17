package com.english.assistant.repository;


import com.english.assistant.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;


import java.util.Optional;



@Repository
public interface UserProfileRepository
        extends JpaRepository<UserProfile, Long> {


    /**
     * 根据用户ID查询学习档案
     */
    Optional<UserProfile> findByUserId(Long userId);


}