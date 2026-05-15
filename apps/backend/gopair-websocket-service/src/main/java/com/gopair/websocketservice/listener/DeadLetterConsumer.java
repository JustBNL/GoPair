package com.gopair.websocketservice.listener;

import com.gopair.common.constants.SystemConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;

/**
 * 死信队列（DLQ）消费者，对落入 dl.queue 的消息进行补偿处理。
 *
 * * [职责]
 * - 消费所有进入 dl.queue 的死信消息
 * - 记录详细死信信息（来源队列、死亡原因、时间）供问题排查
 * - 手动 ACK，确保死信被消费后不重新入队
 *
 * * [死信来源]
 * - websocket.chat / signaling / file / system / offline 队列的消息超时或超出 maxLength
 * - BusinessMessageListener 消费时抛异常且 basicNack(requeue=false)
 *
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadLetterConsumer {

    /**
     * 消费死信消息。
     * 死信进入 DLQ 后，由本 Consumer 记录并消费，防止无限重试。
     * requeue=false 确保消息被正式 ACK 后不会再次入队。
     *
     * @param message      原始 AMQP 消息
     * @param rawMessage   原始消息对象（包含 x-death 等元数据）
     * @param channel      AMQP Channel，用于手动确认
     * @param deliveryTag  消息投递标签
     */
    @RabbitListener(queues = SystemConstants.DL_QUEUE)
    public void handleDeadLetter(Message message, Message rawMessage,
                                Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            var headers = message.getMessageProperties().getHeaders();

            String xFirstDeathExchange = getHeader(headers, "x-first-death-exchange");
            String xFirstDeathQueue = getHeader(headers, "x-first-death-queue");
            String xFirstDeathReason = getHeader(headers, "x-first-death-reason");
            String xDeath = getHeader(headers, "x-death");
            String messageId = message.getMessageProperties().getMessageId();
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            String body = new String(message.getBody());

            log.error("[DLQ] 收到死信消息: messageId={}, firstDeathExchange={}, firstDeathQueue={}, " +
                            "firstDeathReason={}, receivedRoutingKey={}, xDeath={}, bodyPreview={}",
                    messageId, xFirstDeathExchange, xFirstDeathQueue, xFirstDeathReason,
                    routingKey, xDeath,
                    body.length() > 200 ? body.substring(0, 200) + "..." : body);

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("[DLQ] 处理死信消息异常 deliveryTag={}", deliveryTag, e);
            try {
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ignore) {}
        }
    }

    private String getHeader(java.util.Map<String, Object> headers, String key) {
        Object value = headers.get(key);
        return value != null ? value.toString() : "N/A";
    }
}
