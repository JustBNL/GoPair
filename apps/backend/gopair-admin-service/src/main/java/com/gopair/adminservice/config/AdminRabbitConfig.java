package com.gopair.adminservice.config;

import com.gopair.common.config.RabbitMQAutoConfiguration;
import com.gopair.common.config.RabbitMQInfrastructureConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 引入 gopair-common 中的 RabbitMQ 配置：
 * - RabbitMQAutoConfiguration：提供 RabbitTemplate Bean
 * - RabbitMQInfrastructureConfig：声明共享 Exchange
 * 使 admin-service 能够向 room.status.change.exchange 发布消息。
 */
@Configuration
@Import({RabbitMQAutoConfiguration.class, RabbitMQInfrastructureConfig.class})
public class AdminRabbitConfig {
}
