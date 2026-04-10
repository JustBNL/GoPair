package com.gopair.websocketservice.util;

import com.gopair.websocketservice.domain.payload.SubscriptionPayload;
import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.exception.PayloadAdaptationException;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 载荷适配器工具类
 * 负责将Map<String,Object>安全转换为强类型业务对象
 * 
 * @author gopair
 */
@Slf4j
public class PayloadAdapter {

    /**
     * 将Map转换为SubscriptionPayload
     * 
     * @param payload 原始载荷Map
     * @return 强类型的SubscriptionPayload对象
     * @throws PayloadAdaptationException 转换失败时抛出
     */
    public static SubscriptionPayload forSubscription(Map<String, Object> payload) {
        if (payload == null) {
            throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_ADAPTATION_ERROR, "Payload cannot be null");
        }

        try {
            log.debug("[载荷适配] 开始转换订阅载荷: {}", payload);

            // 安全转换userId
            Long userId = extractLongValue(payload, "userId", true);
            
            // 安全转换channel
            String channel = extractStringValue(payload, "channel", true);
            
            // 安全转换eventTypes
            Set<String> eventTypes = extractStringSetValue(payload, "eventTypes", false);
            if (eventTypes == null || eventTypes.isEmpty()) {
                eventTypes = Set.of("default");
            }

            // 构建SubscriptionPayload对象
            SubscriptionPayload subscriptionPayload = SubscriptionPayload.builder()
                    .userId(userId)
                    .channel(channel)
                    .eventTypes(eventTypes)
                    .source("manual")
                    .build();

            log.debug("[载荷适配] 转换成功: userId={}, channel={}, eventTypes={}", 
                    userId, channel, eventTypes);

            return subscriptionPayload;

        } catch (Exception e) {
            log.error("[载荷适配] 转换订阅载荷失败: payload={}", payload, e);
            throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_ADAPTATION_ERROR, "Failed to adapt subscription payload", e);
        }
    }

    /**
     * 安全提取Long值
     * 支持Integer -> Long转换
     * 
     * @param map 源Map
     * @param key 键名
     * @param required 是否必需
     * @return Long值
     */
    public static Long extractLongValue(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        
        if (value == null) {
            if (required) {
                throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_FIELD_MISSING, 
                    String.format("Required field '%s' is missing", key));
            }
            return null;
        }

        if (value instanceof Long) {
            return (Long) value;
        }

        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }

        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_TYPE_MISMATCH,
                    String.format("Cannot parse '%s' as Long for field '%s'", value, key), e);
            }
        }

        throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_TYPE_MISMATCH,
            String.format("Cannot convert %s to Long for field '%s'", value.getClass().getSimpleName(), key));
    }

    /**
     * 安全提取String值
     * 
     * @param map 源Map
     * @param key 键名
     * @param required 是否必需
     * @return String值
     */
    public static String extractStringValue(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        
        if (value == null) {
            if (required) {
                throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_FIELD_MISSING, 
                    String.format("Required field '%s' is missing", key));
            }
            return null;
        }

        if (value instanceof String) {
            String stringValue = (String) value;
            stringValue = stringValue.trim();
            if (stringValue.isEmpty()) {
                if (required) {
                    throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_FIELD_MISSING, 
                        String.format("Required field '%s' cannot be empty", key));
                }
                return null;
            }
            return stringValue;
        }

        // 其他类型转换为String
        return value.toString();
    }

    /**
     * 安全提取String Set值
     * 支持List<String> -> Set<String>转换
     * 
     * @param map 源Map
     * @param key 键名
     * @param required 是否必需
     * @return Set<String>值
     */
    @SuppressWarnings("unchecked")
    public static Set<String> extractStringSetValue(Map<String, Object> map, String key, boolean required) {
        Object value = map.get(key);
        
        if (value == null) {
            if (required) {
                throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_FIELD_MISSING, 
                    String.format("Required field '%s' is missing", key));
            }
            return null;
        }

        if (value instanceof Set) {
            try {
                return (Set<String>) value;
            } catch (ClassCastException e) {
                // 尝试转换Set中的元素
                Set<?> rawSet = (Set<?>) value;
                return rawSet.stream()
                        .map(Object::toString)
                        .collect(HashSet::new, Set::add, Set::addAll);
            }
        }

        if (value instanceof List) {
            try {
                List<String> list = (List<String>) value;
                return new HashSet<>(list);
            } catch (ClassCastException e) {
                // 尝试转换List中的元素
                List<?> rawList = (List<?>) value;
                return rawList.stream()
                        .map(Object::toString)
                        .collect(HashSet::new, Set::add, Set::addAll);
            }
        }

        if (value instanceof String) {
            // 单个String值转换为Set
            return Set.of((String) value);
        }

        throw new PayloadAdaptationException(WebSocketErrorCode.PAYLOAD_TYPE_MISMATCH,
            String.format("Cannot convert %s to Set<String> for field '%s'", value.getClass().getSimpleName(), key));
    }
} 