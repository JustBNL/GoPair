package com.gopair.common.util;

import com.gopair.common.constants.MessageConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * RabbitMQ 消费端追踪上下文支持工具
 *
 * 供 MQ 消费者使用，从消息头中恢复 traceId/userId/nickname 到 MDC，
 * 确保消费端日志与上游发送端日志拥有相同的 traceId，实现跨 MQ 的全链路追踪。
 *
 * 使用方式：
 * <pre>
 * {@literal @}RabbitListener(queues = "some.queue")
 * public void onMessage(Message message) {
 *     tracingAmqpConsumerSupport.runWithTracing(message, () -> {
 *         // 业务逻辑，此处 MDC 已包含 traceId/userId/nickname
 *     });
 * }
 * </pre>
 *
 * @author gopair
 */
@Slf4j
@Component
@ConditionalOnClass(name = "org.springframework.amqp.core.Message")
public class TracingAmqpConsumerSupport {

    /** 消息头 key：追踪 ID */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    /** 消息头 key：用户 ID */
    public static final String HEADER_USER_ID = "X-User-Id";
    /** 消息头 key：用户昵称 */
    public static final String HEADER_NICKNAME = "X-Nickname";

    /**
     * 从 AMQP 消息头恢复追踪上下文，执行任务后清理
     *
     * @param message AMQP 消息（org.springframework.amqp.core.Message）
     * @param task    需要在追踪上下文中执行的业务逻辑
     */
    public void runWithTracing(org.springframework.amqp.core.Message message, Runnable task) {
        String traceId = null;
        String userId = null;
        String nickname = null;
        boolean tracingRestored = false;

        try {
            // 从消息头提取追踪信息
            if (message != null && message.getMessageProperties() != null) {
                var headers = message.getMessageProperties().getHeaders();
                traceId = getHeaderStr(headers, HEADER_TRACE_ID);
                userId = getHeaderStr(headers, HEADER_USER_ID);
                nickname = getHeaderStr(headers, HEADER_NICKNAME);
            }

            // 写入 MDC
            if (StringUtils.hasText(traceId)) {
                MDC.put(MessageConstants.MDC_TRACE_ID, traceId);
                tracingRestored = true;
            }
            if (StringUtils.hasText(userId)) {
                MDC.put(MessageConstants.MDC_USER_ID, userId);
            }
            if (StringUtils.hasText(nickname)) {
                MDC.put(MessageConstants.MDC_NICKNAME, nickname);
            }

            if (tracingRestored) {
                log.debug("[MQ追踪] 已从消息头恢复追踪上下文 - traceId={}, userId={}, nickname={}",
                        traceId, userId, nickname);
            } else {
                log.debug("[MQ追踪] 消息头中无追踪信息，使用当前上下文");
            }

            // 执行业务逻辑
            task.run();

        } finally {
            // 清理本次写入的 MDC 条目，避免污染线程池中后续任务
            if (tracingRestored) {
                MDC.remove(MessageConstants.MDC_TRACE_ID);
            }
            if (StringUtils.hasText(userId)) {
                MDC.remove(MessageConstants.MDC_USER_ID);
            }
            if (StringUtils.hasText(nickname)) {
                MDC.remove(MessageConstants.MDC_NICKNAME);
            }
        }
    }

    /**
     * 安全地从消息头 Map 中获取字符串值
     */
    private String getHeaderStr(java.util.Map<String, Object> headers, String key) {
        if (headers == null) {
            return null;
        }
        Object val = headers.get(key);
        return val != null ? val.toString() : null;
    }
}
