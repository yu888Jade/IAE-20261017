package com.english.assistant.controller;

import com.english.assistant.security.CurrentUserService;
import com.english.assistant.service.StudyPlanService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/plan")
public class PlanController {
    private final StudyPlanService plans;
    private final CurrentUserService current;

    public PlanController(StudyPlanService plans, CurrentUserService current) {
        this.plans = plans;
        this.current = current;
    }

    @GetMapping
    public ResponseEntity<StudyPlanService.PlanResponse> get() {
        return ResponseEntity.ok(plans.getPlan(current.id()));
    }

    @PostMapping("/regenerate")
    public ResponseEntity<StudyPlanService.PlanResponse> regenerate() {
        return ResponseEntity.ok(plans.regenerate(current.id()));
    }
}
