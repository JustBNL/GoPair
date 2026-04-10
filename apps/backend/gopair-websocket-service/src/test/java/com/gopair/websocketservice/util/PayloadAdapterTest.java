package com.gopair.websocketservice.util;

import com.gopair.websocketservice.domain.payload.SubscriptionPayload;
import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.exception.PayloadAdaptationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PayloadAdapter 单元测试（新增）。
 *
 * * [核心策略]
 * - forSubscription：将 Map<String,Object> 安全转换为 SubscriptionPayload。
 * - extractLongValue：支持 Integer → Long、String → Long 自动转换。
 * - extractStringSetValue：支持 List → Set、String → Set 自动转换。
 *
 * * [覆盖场景]
 * - Happy Path：正常类型转换。
 * - Negative：类型转换失败抛 PayloadAdaptationException。
 * - Edge：空字符串、空集合。
 */
public class PayloadAdapterTest {

    // ==================== forSubscription ====================

    @Nested
    @DisplayName("Happy Path：forSubscription 正常转换")
    class ForSubscriptionTests {

        @Test
        @DisplayName("正常转换包含所有字段的 payload")
        void testForSubscriptionSuccess() {
            Map<String, Object> payload = Map.of(
                    "userId", 12345L,
                    "channel", "user:12345",
                    "eventTypes", Set.of("message", "typing"),
                    "source", "manual"
            );

            SubscriptionPayload result = PayloadAdapter.forSubscription(payload);

            assertEquals(12345L, result.getUserId());
            assertEquals("user:12345", result.getChannel());
            assertEquals(Set.of("message", "typing"), result.getEventTypes());
            assertEquals("manual", result.getSource());
        }

        @Test
        @DisplayName("eventTypes 为 null 时使用默认值 Set.of(\"default\")")
        void testForSubscriptionEventTypesDefault() {
            Map<String, Object> payload = Map.of(
                    "userId", 11111L,
                    "channel", "system:test"
            );

            SubscriptionPayload result = PayloadAdapter.forSubscription(payload);

            assertEquals(Set.of("default"), result.getEventTypes());
        }

        @Test
        @DisplayName("source 为 null 时默认为 \"manual\"")
        void testForSubscriptionSourceDefault() {
            Map<String, Object> payload = Map.of(
                    "userId", 22222L,
                    "channel", "system:test2",
                    "eventTypes", Set.of("message")
            );

            SubscriptionPayload result = PayloadAdapter.forSubscription(payload);

            assertEquals("manual", result.getSource());
        }

        @Test
        @DisplayName("isValid 返回 true 当所有字段有效")
        void testSubscriptionPayloadIsValid() {
            Map<String, Object> payload = Map.of(
                    "userId", 33333L,
                    "channel", "room:chat:33333",
                    "eventTypes", Set.of("message")
            );

            SubscriptionPayload result = PayloadAdapter.forSubscription(payload);

            assertTrue(result.isValid());
        }

        @Test
        @DisplayName("userId 为负数时 isValid 返回 false")
        void testSubscriptionPayloadInvalidUserId() {
            Map<String, Object> payload = Map.of(
                    "userId", -1L,
                    "channel", "room:test",
                    "eventTypes", Set.of("message")
            );

            SubscriptionPayload result = PayloadAdapter.forSubscription(payload);

            assertFalse(result.isValid());
        }
    }

    // ==================== extractLongValue ====================

    @Nested
    @DisplayName("Happy Path：extractLongValue 类型转换")
    class ExtractLongValueTests {

        @Test
        @DisplayName("Long 类型直接返回")
        void testLongValue() {
            Map<String, Object> map = Map.of("field", 100L);
            assertEquals(100L, PayloadAdapter.extractLongValue(map, "field", true));
        }

        @Test
        @DisplayName("Integer 类型自动转换为 Long")
        void testIntegerToLong() {
            Map<String, Object> map = Map.of("field", 200);
            assertEquals(200L, PayloadAdapter.extractLongValue(map, "field", true));
        }

        @Test
        @DisplayName("String 类型数字自动转换为 Long")
        void testStringToLong() {
            Map<String, Object> map = Map.of("field", "300");
            assertEquals(300L, PayloadAdapter.extractLongValue(map, "field", true));
        }

        @Test
        @DisplayName("required=false 时字段缺失返回 null")
        void testMissingFieldReturnsNull() {
            Map<String, Object> map = Map.of();
            assertNull(PayloadAdapter.extractLongValue(map, "missing", false));
        }
    }

    @Nested
    @DisplayName("Negative Path：extractLongValue 异常处理")
    class ExtractLongValueNegativeTests {

        @Test
        @DisplayName("required=true 时字段缺失抛 PayloadAdaptationException")
        void testMissingRequiredFieldThrows() {
            Map<String, Object> map = Map.of();

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.extractLongValue(map, "missing", true)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_FIELD_MISSING, ex.getErrorCode());
            assertTrue(ex.getMessage().contains("missing"));
        }

        @Test
        @DisplayName("非数字 String 抛 PayloadAdaptationException")
        void testNonNumericStringThrows() {
            Map<String, Object> map = Map.of("field", "not-a-number");

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.extractLongValue(map, "field", true)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_TYPE_MISMATCH, ex.getErrorCode());
        }

        @Test
        @DisplayName("不支持的类型抛 PayloadAdaptationException")
        void testUnsupportedTypeThrows() {
            Map<String, Object> map = Map.of("field", new Object());

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.extractLongValue(map, "field", true)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_TYPE_MISMATCH, ex.getErrorCode());
        }
    }

    // ==================== extractStringSetValue ====================

    @Nested
    @DisplayName("Happy Path：extractStringSetValue 类型转换")
    class ExtractStringSetValueTests {

        @Test
        @DisplayName("Set<String> 直接返回")
        void testSetValue() {
            Set<String> original = Set.of("a", "b", "c");
            Map<String, Object> map = Map.of("field", original);

            Set<String> result = PayloadAdapter.extractStringSetValue(map, "field", true);

            assertEquals(original, result);
        }

        @Test
        @DisplayName("List<String> 自动转换为 Set")
        void testListToSet() {
            Map<String, Object> map = Map.of("field", List.of("x", "y"));

            Set<String> result = PayloadAdapter.extractStringSetValue(map, "field", true);

            assertEquals(Set.of("x", "y"), result);
        }

        @Test
        @DisplayName("单 String 值自动转换为 Set")
        void testStringToSet() {
            Map<String, Object> map = Map.of("field", "single");

            Set<String> result = PayloadAdapter.extractStringSetValue(map, "field", true);

            assertEquals(Set.of("single"), result);
        }

        @Test
        @DisplayName("required=false 时字段缺失返回 null")
        void testMissingFieldReturnsNullSet() {
            Map<String, Object> map = Map.of();
            assertNull(PayloadAdapter.extractStringSetValue(map, "missing", false));
        }
    }

    @Nested
    @DisplayName("Negative Path：extractStringSetValue 异常处理")
    class ExtractStringSetValueNegativeTests {

        @Test
        @DisplayName("required=true 时字段缺失抛异常")
        void testMissingRequiredSetFieldThrows() {
            Map<String, Object> map = Map.of();

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.extractStringSetValue(map, "missing", true)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_FIELD_MISSING, ex.getErrorCode());
        }

        @Test
        @DisplayName("不支持的类型抛 PayloadAdaptationException")
        void testUnsupportedSetTypeThrows() {
            Map<String, Object> map = Map.of("field", 123);

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.extractStringSetValue(map, "field", true)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_TYPE_MISMATCH, ex.getErrorCode());
        }
    }

    // ==================== extractStringValue ====================

    @Nested
    @DisplayName("Edge Cases：extractStringValue 空字符串校验")
    class ExtractStringValueEdgeTests {

        @Test
        @DisplayName("空字符串在 required=true 时视为缺失并抛异常")
        void testEmptyStringIsMissingWhenRequired() {
            Map<String, Object> map = Map.of("field", "   ");

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.extractStringValue(map, "field", true)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_FIELD_MISSING, ex.getErrorCode());
        }

        @Test
        @DisplayName("required=false 时空字符串返回 null")
        void testEmptyStringReturnsNullWhenOptional() {
            Map<String, Object> map = Map.of("field", "   ");

            String result = PayloadAdapter.extractStringValue(map, "field", false);

            assertNull(result);
        }

        @Test
        @DisplayName("非 String 类型自动调用 toString()")
        void testNonStringConvertsToString() {
            Map<String, Object> map = Map.of("field", 999);

            String result = PayloadAdapter.extractStringValue(map, "field", true);

            assertEquals("999", result);
        }
    }

    // ==================== forSubscription Negative ====================

    @Nested
    @DisplayName("Negative Path：forSubscription 异常处理")
    class ForSubscriptionNegativeTests {

        @Test
        @DisplayName("payload 为 null 时抛 PayloadAdaptationException")
        void testNullPayloadThrows() {
            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.forSubscription(null)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_ADAPTATION_ERROR, ex.getErrorCode());
        }

        @Test
        @DisplayName("channel 为 null 时抛 PayloadAdaptationException")
        void testNullChannelThrows() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", 11111L);
            payload.put("channel", null);

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.forSubscription(payload)
            );

            // channel 为 null 时统一包装为 PAYLOAD_ADAPTATION_ERROR
            assertEquals(WebSocketErrorCode.PAYLOAD_ADAPTATION_ERROR, ex.getErrorCode());
        }

        @Test
        @DisplayName("userId 为 null 时抛 PayloadAdaptationException")
        void testNullUserIdThrows() {
            Map<String, Object> payload = new HashMap<>();
            payload.put("userId", null);
            payload.put("channel", "test");

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.forSubscription(payload)
            );

            // userId 为 null 时统一包装为 PAYLOAD_ADAPTATION_ERROR
            assertEquals(WebSocketErrorCode.PAYLOAD_ADAPTATION_ERROR, ex.getErrorCode());
        }

        @Test
        @DisplayName("channel 为空字符串时抛 PayloadAdaptationException（trim 后为空）")
        void testEmptyChannelThrows() {
            Map<String, Object> payload = Map.of(
                    "userId", 44444L,
                    "channel", "",
                    "eventTypes", Set.of("message")
            );

            PayloadAdaptationException ex = assertThrows(
                    PayloadAdaptationException.class,
                    () -> PayloadAdapter.forSubscription(payload)
            );

            assertEquals(WebSocketErrorCode.PAYLOAD_ADAPTATION_ERROR, ex.getErrorCode());
        }
    }
}
