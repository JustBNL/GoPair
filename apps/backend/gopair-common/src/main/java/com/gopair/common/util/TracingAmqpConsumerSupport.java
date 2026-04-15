package com.gopair.common.util;

import com.gopair.common.constants.SystemConstants;
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

    /**
     * 从 AMQP 消息头恢复追踪上下文，执行任务后清理
     *
     * * [核心策略]
     * - 无论消息头是否含 traceId，finally 块始终执行清理（MDC.remove() 幂等），防止线程池复用时污染
     * - traceId/userId/nickname 三项各自独立追踪写入状态，互不耦合
     *
     * * [执行链路]
     * 1. 从消息头提取 traceId/userId/nickname
     * 2. 若值有效则写入 MDC，记录该键被写入
     * 3. 执行业务逻辑
     * 4. finally 块中，只清理本次写入过的 MDC 键（未写入则不清理，避免误覆盖其他 Span 遗留的值）
     *
     * @param message AMQP 消息（org.springframework.amqp.core.Message）
     * @param task    需要在追踪上下文中执行的业务逻辑
     */
    public void runWithTracing(org.springframework.amqp.core.Message message, Runnable task) {
        boolean traceIdWritten = false;
        boolean userIdWritten = false;
        boolean nicknameWritten = false;

        try {
            if (message != null && message.getMessageProperties() != null) {
                var headers = message.getMessageProperties().getHeaders();
                String traceId = getHeaderStr(headers, SystemConstants.HEADER_TRACE_ID);
                String userId = getHeaderStr(headers, SystemConstants.HEADER_USER_ID);
                String nickname = getHeaderStr(headers, SystemConstants.HEADER_NICKNAME);

                if (StringUtils.hasText(traceId)) {
                    MDC.put(SystemConstants.MDC_TRACE_ID, traceId);
                    traceIdWritten = true;
                }
                if (StringUtils.hasText(userId)) {
                    MDC.put(SystemConstants.MDC_USER_ID, userId);
                    userIdWritten = true;
                }
                if (StringUtils.hasText(nickname)) {
                    MDC.put(SystemConstants.MDC_NICKNAME, nickname);
                    nicknameWritten = true;
                }
            }

            if (traceIdWritten) {
                log.debug("[MQ追踪] 已从消息头恢复追踪上下文 - traceId={}", MDC.get(SystemConstants.MDC_TRACE_ID));
            } else {
                log.debug("[MQ追踪] 消息头中无追踪信息，使用当前上下文");
            }

            task.run();
        } finally {
            if (traceIdWritten) {
                MDC.remove(SystemConstants.MDC_TRACE_ID);
            }
            if (userIdWritten) {
                MDC.remove(SystemConstants.MDC_USER_ID);
            }
            if (nicknameWritten) {
                MDC.remove(SystemConstants.MDC_NICKNAME);
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
