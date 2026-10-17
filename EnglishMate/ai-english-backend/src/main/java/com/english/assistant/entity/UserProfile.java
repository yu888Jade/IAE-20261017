package com.english.assistant.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;


@Entity
@Table(name = "user_profile")
public class UserProfile {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /**
     * 用户ID
     *
     * 当前阶段不接入复杂登录系统，
     * 先用于实现用户数据隔离。
     */
    @Column(name = "user_id", unique = true)
    private Long userId;



    @NotBlank(
            message = "学习目标不能为空"
    )
    private String goal;



    @NotBlank(
            message = "当前英语水平不能为空"
    )
    private String level;



    @NotBlank(
            message = "每日学习时间不能为空"
    )
    private String studyTime;



    @NotBlank(
            message = "薄弱方向不能为空"
    )
    private String weakness;



    private LocalDateTime createTime;


    private LocalDateTime updateTime;



    public UserProfile() {

    }



    public UserProfile(
            Long userId,
            String goal,
            String level,
            String studyTime,
            String weakness
    ) {

        this.userId = userId;
        this.goal = goal;
        this.level = level;
        this.studyTime = studyTime;
        this.weakness = weakness;


        LocalDateTime now =
                LocalDateTime.now();


        this.createTime = now;
        this.updateTime = now;
    }




    @PrePersist
    public void prePersist() {

        LocalDateTime now =
                LocalDateTime.now();


        if(createTime == null){

            createTime = now;

        }


        updateTime = now;

    }




    @PreUpdate
    public void preUpdate(){

        updateTime =
                LocalDateTime.now();

    }




    public Long getId() {

        return id;

    }



    public void setId(Long id) {

        this.id = id;

    }




    public Long getUserId() {

        return userId;

    }



    public void setUserId(Long userId) {

        this.userId = userId;

    }




    public String getGoal() {

        return goal;

    }



    public void setGoal(String goal) {

        this.goal = goal;

    }




    public String getLevel() {

        return level;

    }



    public void setLevel(String level) {

        this.level = level;

    }




    public String getStudyTime() {

        return studyTime;

    }



    public void setStudyTime(String studyTime) {

        this.studyTime = studyTime;

    }




    public String getWeakness() {

        return weakness;

    }



    public void setWeakness(String weakness) {

        this.weakness = weakness;

    }




    public LocalDateTime getCreateTime() {

        return createTime;

    }



    public void setCreateTime(LocalDateTime createTime) {

        this.createTime = createTime;

    }




    public LocalDateTime getUpdateTime() {

        return updateTime;

    }



    public void setUpdateTime(LocalDateTime updateTime) {

        this.updateTime = updateTime;

    }

}