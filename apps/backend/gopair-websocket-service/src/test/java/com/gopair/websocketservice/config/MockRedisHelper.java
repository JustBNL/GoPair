package com.gopair.websocketservice.config;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 内嵌内存 Redis Mock，帮助类。
 *
 * 所有 doAnswer / when 调用都在静态方法中完成，
 * 避免 Spring CGLIB 代理在构造 TestConfig Bean 时遇到复杂的 Lambda 闭包。
 */
public final class MockRedisHelper {

    private MockRedisHelper() {}

    @SuppressWarnings("unchecked")
    public static RedisTemplate<String, Object> createMockRedisTemplate() {
        Map<String, Object> memoryStore = new ConcurrentHashMap<>();
        Map<String, Object> setStore = new ConcurrentHashMap<>();

        RedisTemplate<String, Object> mockRedis = mock(RedisTemplate.class);

        // 关键：先 mock RedisConnection，避免 execute() 内部 NPE
        RedisConnection mockConnection = mock(RedisConnection.class);
        when(mockConnection.isPipelined()).thenReturn(false);

        RedisConnectionFactory mockConnectionFactory = mock(RedisConnectionFactory.class);
        when(mockConnectionFactory.getConnection()).thenReturn(mockConnection);
        when(mockRedis.getConnectionFactory()).thenReturn(mockConnectionFactory);

        // HashOperations
        HashOperations<String, Object, Object> mockHashOps = mock(HashOperations.class);
        when(mockRedis.opsForHash()).thenReturn(mockHashOps);

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Map<String, Object> vals = inv.getArgument(2);
            memoryStore.put(key + "_hash", new ConcurrentHashMap<>(vals));
            return null;
        }).when(mockHashOps).putAll(anyString(), any(Map.class));

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Object hk = inv.getArgument(1);
            Object val = inv.getArgument(2);
            ((ConcurrentHashMap<String, Object>) memoryStore.computeIfAbsent(key + "_hash", k -> new ConcurrentHashMap<>()))
                    .put(hk.toString(), val);
            return null;
        }).when(mockHashOps).put(anyString(), any(), any());

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Object hk = inv.getArgument(1);
            ConcurrentHashMap<String, Object> hash = (ConcurrentHashMap<String, Object>) memoryStore.get(key + "_hash");
            return hash != null ? hash.get(hk.toString()) : null;
        }).when(mockHashOps).get(anyString(), any());

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            ConcurrentHashMap<String, Object> hash = (ConcurrentHashMap<String, Object>) memoryStore.get(key + "_hash");
            return hash != null ? new ConcurrentHashMap<>(hash) : new ConcurrentHashMap<>();
        }).when(mockHashOps).entries(anyString());

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Object[] hkeys = inv.getArgument(1);
            ConcurrentHashMap<String, Object> hash = (ConcurrentHashMap<String, Object>) memoryStore.get(key + "_hash");
            Object[] result = new Object[hkeys.length];
            if (hash != null) {
                for (int i = 0; i < hkeys.length; i++) {
                    result[i] = hash.get(hkeys[i].toString());
                }
            }
            return result;
        }).when(mockHashOps).multiGet(anyString(), anyList());

        doAnswer(inv -> {
            String key = inv.getArgument(0);
            Object[] fields = inv.getArgument(1);
            ConcurrentHashMap<String, Object> hash = (ConcurrentHashMap<String, Object>) memoryStore.get(key + "_hash");
            long deleted = 0;
            if (hash != null) {
                for (Object f : fields) {
                    if (hash.remove(f.toString()) != null) deleted++;
                }
            }
            return deleted;
        }).when(mockHashOps).delete(anyString(), any(Object[].class));

        // ValueOperations
        ValueOperations<String, Object> mockValueOps = mock(ValueOperations.class);
        when(mockRedis.opsForValue()).thenReturn(mockValueOps);

        doAnswer(inv -> {
            memoryStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockValueOps).set(anyString(), any());

        doAnswer(inv -> {
            String k = inv.getArgument(0);
            Object v = inv.getArgument(1);
            Long exp = inv.getArgument(2);
            memoryStore.put(k, v);
            memoryStore.put(k + "_expire", exp);
            return null;
        }).when(mockValueOps).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        doAnswer(inv -> {
            memoryStore.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(mockValueOps).increment(anyString());

        doAnswer(inv -> memoryStore.get(inv.getArgument(0)))
                .when(mockValueOps).get(anyString());

        // SetOperations
        SetOperations<String, Object> mockSetOps = mock(SetOperations.class);
        when(mockRedis.opsForSet()).thenReturn(mockSetOps);

        doAnswer(inv -> {
            String k = inv.getArgument(0);
            Object m = inv.getArgument(1);
            ((Set<Object>) setStore.computeIfAbsent(k, x -> ConcurrentHashMap.newKeySet())).add(m);
            return 1L;
        }).when(mockSetOps).add(anyString(), any());

        doAnswer(inv -> {
            String k = inv.getArgument(0);
            Object m = inv.getArgument(1);
            Set<Object> s = (Set<Object>) setStore.get(k);
            return s != null && s.remove(m) ? 1L : 0L;
        }).when(mockSetOps).remove(anyString(), any());

        doAnswer(inv -> {
            String k = inv.getArgument(0);
            Set<Object> s = (Set<Object>) setStore.get(k);
            return (s == null || s.isEmpty()) ? null : s;
        }).when(mockSetOps).members(anyString());

        // 其他操作
        when(mockRedis.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        doAnswer(inv -> {
            String k = inv.getArgument(0);
            Object exp = memoryStore.get(k + "_expire");
            if (exp instanceof Long) return (Long) exp;
            if (exp instanceof Integer) return ((Integer) exp).longValue();
            return -1L;
        }).when(mockRedis).getExpire(anyString());

        // keys(String): 直接返回空集，不走 execute 路径
        doAnswer(inv -> Collections.emptySet()).when(mockRedis).keys(anyString());

        // delete(String): 兼容单个 key
        when(mockRedis.delete(anyString())).thenAnswer(inv -> {
            String k = inv.getArgument(0);
            setStore.remove(k);
            return memoryStore.remove(k) != null;
        });

        // delete(Set<String>): 兼容批量删除
        when(mockRedis.delete(anySet())).thenAnswer(inv -> {
            Set<String> keys = inv.getArgument(0);
            long deleted = 0;
            for (String k : keys) {
                if (memoryStore.remove(k) != null) deleted++;
                setStore.remove(k);
            }
            return deleted;
        });

        // execute(RedisCallback<T>): 核心修复——不实际获取连接，直接返回 null/空集
        // 这是 RedisTemplate 内部 keys()、delete(Set) 等方法的实际执行路径
        doAnswer(inv -> {
            RedisCallback<?> callback = inv.getArgument(0);
            // 对于 keys() 相关的 RedisCallback，返回空集合
            // 对于其他操作，返回 null（空 Hash 等）
            return null;
        }).when(mockRedis).execute(any(RedisCallback.class));

        doAnswer(inv -> {
            RedisCallback<?> callback = inv.getArgument(1);
            return null;
        }).when(mockRedis).execute(any(RedisCallback.class), anyBoolean());

        // execute(SessionCallback<T>)
        doAnswer(inv -> {
            SessionCallback<?> callback = inv.getArgument(0);
            return null;
        }).when(mockRedis).execute(any(SessionCallback.class));

        // execute(RedisScript, List<String>, Object...): 令牌桶 Lua 脚本
        // 使用 any() 匹配 varargs（Object...），兼容任意数量的参数
        doAnswer(inv -> 1L).when(mockRedis).execute(
                any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList(),
                any(Object[].class));

        // 同时 stub 2 参数版本的 execute(RedisScript, List)（无 varargs）
        doAnswer(inv -> 1L).when(mockRedis).execute(
                any(org.springframework.data.redis.core.script.RedisScript.class),
                anyList());

        return mockRedis;
    }
}
