package com.english.assistant.entity;
import jakarta.persistence.*; import java.time.LocalDateTime;
@Entity @Table(name="chat_session")
public class ChatSession {
 @Id @Column(length=80) private String id;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(nullable=false,length=80) private String title;
 @Column(nullable=false,length=20) private String mode;
 private LocalDateTime createTime; private LocalDateTime updateTime;
 public ChatSession(){} public ChatSession(String id,Long uid,String title,String mode){this.id=id;userId=uid;this.title=title;this.mode=mode;createTime=updateTime=LocalDateTime.now();}
 @PreUpdate void update(){updateTime=LocalDateTime.now();}
 public String getId(){return id;} public void setId(String v){id=v;} public Long getUserId(){return userId;} public void setUserId(Long v){userId=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getMode(){return mode;} public void setMode(String v){mode=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;} public LocalDateTime getUpdateTime(){return updateTime;} public void setUpdateTime(LocalDateTime v){updateTime=v;}
}
