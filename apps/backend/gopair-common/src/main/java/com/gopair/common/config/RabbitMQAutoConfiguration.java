package com.gopair.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /**
     * 配置消息转换器
     */
    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
} 