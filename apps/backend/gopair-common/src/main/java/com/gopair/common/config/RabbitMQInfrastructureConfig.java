package com.gopair.common.config;

import com.gopair.common.constants.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 共享基础设施配置
 * 仅声明所有服务共用的 Exchange，打破服务间启动顺序依赖。
 *
 * <p>Queue 和 Binding 由各服务自行维护：
 * <ul>
 *   <li>websocket-service：websocket.* 系列队列 + user.offline.queue</li>
 *   <li>room-service：room.join/leave 系列队列 + user.offline.queue binding</li>
 *   <li>voice-service：voice.* 系列队列</li>
 * </ul>
 *
 * <p>DLX/DLQ 在此声明，供所有服务复用。
 *
 * <p>Queue/Binding 不放在本配置中的原因：若在 gopair-common 的 auto-import 配置中声明，
 * 会与业务服务自身的同名 Queue Bean 产生 {@code @ConditionalOnMissingBean} 加载顺序陷阱——
 * common 的 Bean 因 auto-import 优先加载而先注册，导致业务服务的个性化参数（TTL、maxLength 等）被静默丢弃。
 * Exchange 无状态，重复声明安全，故保留于此。
 *
 * @author gopair
 */
@Slf4j
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQInfrastructureConfig {

    /**
     * WebSocket 消息统一交换机（Topic 类型，durable）
     */
    @Bean
    @ConditionalOnMissingBean(name = "websocketTopicExchange")
    public TopicExchange websocketTopicExchange() {
        return ExchangeBuilder
                .topicExchange(SystemConstants.WEBSOCKET_EXCHANGE)
                .durable(true)
                .build();
    }

    // ==================== DLX & DLQ ====================

    @Bean
    public TopicExchange dlExchange() {
        return ExchangeBuilder.topicExchange(SystemConstants.DL_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue dlQueue() {
        return QueueBuilder.durable(SystemConstants.DL_QUEUE).build();
    }

    @Bean
    public Binding dlQueueBinding() {
        return BindingBuilder
                .bind(dlQueue())
                .to(dlExchange())
                .with(SystemConstants.DL_ROUTING_KEY_ALL);
    }

    // ==================== Room Status Change ====================

    @Bean
    public TopicExchange roomStatusChangeExchange() {
        return ExchangeBuilder.topicExchange(SystemConstants.EXCHANGE_ROOM_STATUS_CHANGE).durable(true).build();
    }
}
