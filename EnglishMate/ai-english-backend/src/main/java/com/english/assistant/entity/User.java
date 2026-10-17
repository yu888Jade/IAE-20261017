package com.english.assistant.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity @Table(name="app_user", uniqueConstraints={@UniqueConstraint(columnNames="username"),@UniqueConstraint(columnNames="email")})
public class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=40) private String username;
 @Column(nullable=false,length=120) private String email;
 @Column(nullable=false,length=100) private String password;
 private LocalDateTime createTime;
 public User(){} public User(String u,String e,String p){username=u;email=e;password=p;createTime=LocalDateTime.now();}
 @PrePersist void pre(){if(createTime==null)createTime=LocalDateTime.now();}
 public Long getId(){return id;} public void setId(Long v){id=v;} public String getUsername(){return username;} public void setUsername(String v){username=v;} public String getEmail(){return email;} public void setEmail(String v){email=v;} public String getPassword(){return password;} public void setPassword(String v){password=v;} public LocalDateTime getCreateTime(){return createTime;} public void setCreateTime(LocalDateTime v){createTime=v;}
}
