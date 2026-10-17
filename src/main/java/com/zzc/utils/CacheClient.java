package com.zzc.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.zzc.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private static final String LOCK_PREFIX = "LOCK:";
    private static final Long LOCK_TTL = 10L;
    private static final Long DATA_TTL = 30L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ExecutorService executorService;

    /**
     * 自旋获取锁，传入唯一lockValue
     */
    private boolean tryLock(String lockKey, int retryTimes, String lockValue) throws InterruptedException {
        while (retryTimes > 0) {
            Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_SHOP_TTL, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(isLock)) {
                return true;
            }
            Thread.sleep(50);
            retryTimes--;
        }
        return false;
    }

    /**
     * Lua原子解锁，判断锁归属，防止误删别人锁
     */
    private void unLock(String lockKey, String lockValue) {
        String luaScript =
                "if redis.call('get', KEYS[1]) == ARGV[1] then\n" +
                        "    return redis.call('del', KEYS[1])\n" +
                        "else\n" +
                        "    return 0\n" +
                        "end";
        stringRedisTemplate.execute(new DefaultRedisScript<>(luaScript, Long.class), Collections.singletonList(lockKey), lockValue);
    }

    /**
     * 以JSON格式序列化任意对象，并携带过期时间
     *
     * @param key
     * @param value
     * @param expireTime
     * @param timeUnit
     */
    public void set(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), expireTime, timeUnit);
    }

    /**
     * 以JSON格式序列化封装后的任意对象及其逻辑过期时间
     *
     * @param key
     * @param value
     * @param expireTime
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long expireTime, TimeUnit timeUnit) {
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(expireTime)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 使用空值缓存解决缓存穿透问题，通过ID查询任意对象缓存
     *
     * @param prefix     缓存前缀
     * @param id
     * @param type       对象类型
     * @param function   查询数据库方法
     * @param expireTime
     * @param timeUnit
     * @param <R>
     * @param <ID>
     * @return 反序列化后的对象
     */
    public <R, ID> R getWithCachePenetrate(String prefix, ID id, Class<R> type, Function<ID, R> function, Long expireTime, TimeUnit timeUnit) {
        // 1.根据id查redis缓存
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            // 2.缓存不存在，查数据库，重建缓存
            R r = function.apply(id);
            if (r != null) {
                // 3.写入redis缓存，使用随机TTL防止缓存雪崩
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), expireTime + (long) (Math.random() * 5), timeUnit);
            } else {
                // 4.防止缓存穿透，设置空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, timeUnit);
            }
            return r;
        } else {
            // 6.缓存存在，判断是否是空值缓存
            if (json.isEmpty()) {
                return null;
            }
        }
        return JSONUtil.toBean(json, type);
    }

    /**
     * 使用互斥锁解决缓存击穿问题，通过ID查询热点数据
     *
     * @param prefix
     * @param id
     * @param type
     * @param function
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R getWithCacheBreakThrough(String prefix, ID id, Class<R> type, Function<ID, R> function) {
        // 使用缓存逻辑过期
        // 1.查询缓存
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            // 2.缓存为空，返回
            return null;
        } else {
            // 3.非空，判断是否过期
            RedisData redisData = JSONUtil.toBean(json, RedisData.class);
            LocalDateTime expireTime = redisData.getExpireTime();
            JSONObject data = (JSONObject) redisData.getData();
            R r = JSONUtil.toBean(data, type);
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 4.缓存未过期，直接返回
                return r;
            } else {
                // 5.缓存已过期，使用setNX互斥锁进行缓存重建
                String lockKey = LOCK_PREFIX + key;
                String lockValue = UUID.randomUUID() + "-" + Thread.currentThread().getId();
                boolean isLock = false;
                isLock = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, lockValue, LOCK_TTL, TimeUnit.SECONDS));
                if (isLock) {
                    // 6.二次检查：重新读取缓存，看看是否已经被其他线程重建
                    String newShopJson = stringRedisTemplate.opsForValue().get(key);
                    if (newShopJson != null) {
                        RedisData newRedisData = JSONUtil.toBean(newShopJson, RedisData.class);
                        if (newRedisData.getExpireTime().isAfter(LocalDateTime.now())) {
                            // 缓存已被其他线程重建，直接返回新数据
                            JSONObject newData = (JSONObject) newRedisData.getData();
                            return JSONUtil.toBean(newData, type);
                        }
                    }
                    // 7.获取锁成功，异步线程重建缓存
                    executorService.submit(() -> {
                        try {
                            // 查数据库
                            R res = function.apply(id);
                            // 写入redis
                            setWithLogicalExpire(key, res, DATA_TTL, TimeUnit.MINUTES);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            // 8.释放锁
                            unLock(lockKey, lockValue);
                        }
                    });
                }
                return r;
            }
        }
    }
}
