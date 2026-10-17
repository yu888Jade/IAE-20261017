package com.english.assistant.service;
import org.springframework.data.redis.core.StringRedisTemplate; import org.springframework.stereotype.Service; import java.time.Duration;
@Service public class RateLimitService {private final StringRedisTemplate redis; public RateLimitService(StringRedisTemplate r){redis=r;} public boolean allow(Long uid){try{String k="ai_limit:user:"+uid; Long n=redis.opsForValue().increment(k); if(n!=null&&n==1)redis.expire(k,Duration.ofMinutes(1)); return n==null||n<=10;}catch(Exception e){return true;}}}
