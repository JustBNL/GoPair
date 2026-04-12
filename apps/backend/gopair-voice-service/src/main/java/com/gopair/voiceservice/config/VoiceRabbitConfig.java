package com.gopair.voiceservice.config;

import com.gopair.common.constants.SystemConstants;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 语音服务 RabbitMQ 配置
 *
 * * [职责范围]
 * - 声明 voice.room.created 队列及其与 websocket.topic Exchange 的绑定
 * - websocket.topic Exchange 由 gopair-common 统一声明，此处注入使用同一个 Bean
 *
 * @author gopair
 */
@Configuration
public class VoiceRabbitConfig {

    @Value("${gopair.mq.voice-room-created-ttl:300000}")
    private int voiceRoomCreatedTtl;

    @Bean
    public Queue voiceRoomCreatedQueue() {
        return QueueBuilder.durable(SystemConstants.QUEUE_VOICE_ROOM_CREATED)
                .ttl(voiceRoomCreatedTtl)
                .build();
    }

    @Bean
    public Binding voiceRoomCreatedBinding(TopicExchange websocketTopicExchange) {
        return BindingBuilder
                .bind(voiceRoomCreatedQueue())
                .to(websocketTopicExchange)
                .with(SystemConstants.ROUTING_KEY_SYSTEM_ROOM);
    }
}
