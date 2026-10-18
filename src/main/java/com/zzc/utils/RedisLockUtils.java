package com.zzc.utils;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
public class RedisLockUtils {
    private static final DefaultRedisScript<Long> SCRIPT;
    static {
        SCRIPT = new DefaultRedisScript<>();
        SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        SCRIPT.setResultType(Long.class);
    }
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public boolean tryLock(String lockKey, String value, long expireTime) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, value, expireTime, TimeUnit.SECONDS));
    }

    /**
     * TODO 非原子删除，可使用lua脚本改造成原子删除
     * @param lockKey
     * @param value
     */
    public void unlock(String lockKey, String value) {
        stringRedisTemplate.execute(
                SCRIPT,
                Collections.singletonList(lockKey),
                value
        );
    }
}
