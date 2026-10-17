package com.english.assistant.repository;
import com.english.assistant.entity.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface StudyPlanRepository extends JpaRepository<StudyPlan,Long> {
    Optional<StudyPlan> findByUserId(Long userId);
}
