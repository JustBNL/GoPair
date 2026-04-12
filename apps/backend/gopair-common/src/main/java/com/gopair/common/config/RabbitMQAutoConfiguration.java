package com.gopair.common.config;

import com.gopair.common.constants.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
import org.springframework.util.StringUtils;

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
     * 全局消息处理器：设置消息持久化并注入追踪上下文消息头
     *
     * 在发送前从 MDC 读取 traceId/userId/nickname，写入 AMQP 消息头，
     * 供消费端通过 TracingAmqpConsumerSupport 恢复追踪上下文，实现跨 MQ 的全链路追踪。
     */
    @Bean
    public MessagePostProcessor persistentMessagePostProcessor() {
        return (Message message) -> {
            try {
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            } catch (Exception e) {
                log.warn("[RabbitMQ] 设置消息持久化属性失败，将继续发送（非致命）：{}", e.getMessage());
            }
            // 注入追踪上下文到消息头
            try {
                String traceId = MDC.get(SystemConstants.MDC_TRACE_ID);
                String userId = MDC.get(SystemConstants.MDC_USER_ID);
                String nickname = MDC.get(SystemConstants.MDC_NICKNAME);
                if (StringUtils.hasText(traceId)) {
                    message.getMessageProperties().setHeader(SystemConstants.HEADER_TRACE_ID, traceId);
                }
                if (StringUtils.hasText(userId)) {
                    message.getMessageProperties().setHeader(SystemConstants.HEADER_USER_ID, userId);
                }
                if (StringUtils.hasText(nickname)) {
                    message.getMessageProperties().setHeader(SystemConstants.HEADER_NICKNAME, nickname);
                }
                log.debug("[RabbitMQ] 已注入追踪消息头 - traceId={}, userId={}, nickname={}",
                        traceId, userId, nickname);
            } catch (Exception e) {
                log.debug("[RabbitMQ] 注入追踪消息头失败（非致命）：{}", e.getMessage());
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
