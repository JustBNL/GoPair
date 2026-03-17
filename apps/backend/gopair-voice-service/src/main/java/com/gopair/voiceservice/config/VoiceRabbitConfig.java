package com.gopair.voiceservice.config;

import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 语音服务 RabbitMQ 配置
 * 声明监听 room_created 事件所需的队列和绑定
 *
 * @author gopair
 */
@Configuration
public class VoiceRabbitConfig {

    @Value("${mq.voice.room-created.queue:voice.room.created.queue}")
    private String roomCreatedQueue;

    /** WebSocket 消息使用的 exchange（与其他服务保持一致） */
    private static final String WEBSOCKET_EXCHANGE = "websocket.topic";

    /** room-service 发送 room_created 事件使用的 routing key */
    private static final String ROOM_CREATED_ROUTING_KEY = "system.room";

    @Bean
    public Queue voiceRoomCreatedQueue() {
        return QueueBuilder.durable(roomCreatedQueue).build();
    }

    @Bean
    public Exchange voiceWebsocketExchange() {
        return ExchangeBuilder.topicExchange(WEBSOCKET_EXCHANGE).durable(true).build();
    }

    @Bean
    public Binding voiceRoomCreatedBinding() {
        return BindingBuilder
                .bind(voiceRoomCreatedQueue())
                .to(voiceWebsocketExchange())
                .with(ROOM_CREATED_ROUTING_KEY)
                .noargs();
    }
}
