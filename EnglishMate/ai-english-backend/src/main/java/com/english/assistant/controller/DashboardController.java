package com.english.assistant.controller;

import com.english.assistant.repository.*;
import com.english.assistant.security.CurrentUserService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final CurrentUserService current;
    private final EssayRecordRepository essayRepo;
    private final MistakeRepository mistakeRepo;
    private final ChatHistoryRepository chatRepo;

    public DashboardController(CurrentUserService c, EssayRecordRepository e, MistakeRepository m, ChatHistoryRepository h){
        current=c; essayRepo=e; mistakeRepo=m; chatRepo=h;
    }

    @GetMapping
    public Map<String,Object> dashboard(){

        Long uid=current.id();


        Map<String,Object> data=new HashMap<>();


        data.put(
                "essayCount",
                essayRepo.countByUserId(uid)
        );


        data.put(
                "mistakeCount",
                mistakeRepo.countByUserId(uid)
        );


        data.put(
                "chatCount",
                chatRepo.countByUserIdAndRole(uid, "user")
        );


        data.put(
                "message",
                "学习数据统计"
        );


        return data;

    }
}
