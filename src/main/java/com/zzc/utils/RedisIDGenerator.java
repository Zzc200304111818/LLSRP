package com.zzc.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDGenerator {
    private static final long BEGIN_TIMESTAMP = 1767225600L; // 以UTC 2026/1/1 00:00:00 作为初始时间戳（秒）

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public long nextID(String keyPrefix) {
        // 生成当前时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        // 生成序列号
        String date = DateTimeFormatter.ofPattern("yyyy:MM:dd").format(now);
        Long count = stringRedisTemplate.opsForValue().increment(keyPrefix + date);
        if (count == null) {
            throw new RuntimeException("序列号生成失败!");
        }
        return timestamp << 32 | count;
    }
}
