package com.gopair.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

// 新增导入
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;

/**
 * RabbitMQ自动配置类
 * 为业务服务提供统一的RabbitMQ配置
 * 
 * @author gopair
 */
@Slf4j
@Configuration
@ConditionalOnClass(RabbitTemplate.class)
public class RabbitMQAutoConfiguration {

    @Value("${gopair.rabbitmq.message-persistence-enabled:true}")
    private boolean messagePersistenceEnabled;

    @Value("${gopair.rabbitmq.publisher-confirm-enabled:true}")
    private boolean publisherConfirmEnabled;

    @Value("${gopair.rabbitmq.publisher-returns-enabled:true}")
    private boolean publisherReturnsEnabled;

    /**
     * 配置消息转换器（使用配置完善的ObjectMapper，保证时间为ISO字符串）
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper baseMapper) {
        ObjectMapper mapper = baseMapper.copy();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return new Jackson2JsonMessageConverter(mapper);
    }

    /**
     * 全局消息持久化处理器：将deliveryMode设置为PERSISTENT
     */
    @Bean
    public MessagePostProcessor persistentMessagePostProcessor() {
        return (Message message) -> {
            try {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            } catch (Exception e) {
                log.warn("[RabbitMQ] 设置消息持久化属性失败，将继续发送（非致命）：{}", e.getMessage());
            }
            return message;
        };
    }

    /**
     * 统一配置RabbitTemplate：
     * - 设置JSON消息转换器
     * - 根据配置开启mandatory、发布确认与返回回调
     * - 在发布前应用消息持久化处理器（可通过配置开关控制）
     */
    @Bean
    @Primary
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         Jackson2JsonMessageConverter messageConverter,
                                         MessagePostProcessor persistentMessagePostProcessor) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        // 返回回调依赖mandatory=true
        if (publisherReturnsEnabled) {
            template.setMandatory(true);
            template.setReturnsCallback(returned -> {
                try {
                    log.error("[RabbitMQ返回] exchange={}, routingKey={}, replyCode={}, replyText={}, messageProperties={}",
                            returned.getExchange(),
                            returned.getRoutingKey(),
                            returned.getReplyCode(),
                            returned.getReplyText(),
                            returned.getMessage() != null ? returned.getMessage().getMessageProperties() : null);
                } catch (Exception e) {
                    log.warn("[RabbitMQ返回] 记录返回回调日志出错：{}", e.getMessage());
                }
            });
        }

        if (publisherConfirmEnabled) {
            template.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
                String cid = correlationData != null ? correlationData.getId() : "null";
                if (ack) {
                    log.info("[RabbitMQ确认] 成功 correlationId={}", cid);
                } else {
                    log.error("[RabbitMQ确认] 失败 correlationId={}, cause={}", cid, cause);
                }
            });
        }

        // 发布前处理：默认启用消息持久化
        if (messagePersistenceEnabled && persistentMessagePostProcessor != null) {
            template.setBeforePublishPostProcessors(persistentMessagePostProcessor);
        }

        return template;
    }
} 