package com.gopair.messageservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 测试专用配置：提供一个 Mock 的 ApplicationEventPublisher
 *
 * 当测试类使用 @MockBean ApplicationEventPublisher 时，
 * Spring 会使用此配置创建的 mock 实例（因为 @Primary）。
 * 其他测试类不使用此 mock 时，不会影响真实的事件发布行为。
 */
@TestConfiguration
public class MockApplicationEventPublisherConfig {

    @Bean
    @Primary
    public ApplicationEventPublisher mockApplicationEventPublisher() {
        return new MockApplicationEventPublisher();
    }

    /**
     * Mock 实现：所有事件发布操作均为空实现，
     * 不建立真实的事件监听链路。
     */
    private static class MockApplicationEventPublisher implements ApplicationEventPublisher {

        @Override
        public void publishEvent(Object event) {
            // 空实现，测试通过 verify(mock).publishEvent() 验证调用
        }
    }
}
