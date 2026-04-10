package com.gopair.voiceservice.config;

import com.gopair.common.constants.MessageConstants;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 语音服务 RabbitMQ 配置
 * 声明监听 room_created 事件所需的队列和绑定
 *
 * <p>注意：websocket.topic Exchange 已由 gopair-common 的 RabbitMQInfrastructureConfig 统一声明，
 * 此处通过方法参数注入使用同一个 Bean，仅声明队列及其与 Exchange 的绑定关系。
 *
 * @author gopair
 */
@Configuration
public class VoiceRabbitConfig {

    @Value("${mq.voice.room-created.queue:voice.room.created.queue}")
    private String roomCreatedQueue;

    @Bean
    public Queue voiceRoomCreatedQueue() {
        return QueueBuilder.durable(roomCreatedQueue).build();
    }

    @Bean
    public Binding voiceRoomCreatedBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder
                .bind(voiceRoomCreatedQueue())
                .to(websocketTopicExchange)
                .with(MessageConstants.ROUTING_KEY_SYSTEM_ROOM);
    }
}
