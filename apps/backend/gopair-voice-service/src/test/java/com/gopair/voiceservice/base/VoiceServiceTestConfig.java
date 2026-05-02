package com.gopair.voiceservice.base;

import com.gopair.common.util.TracingAmqpConsumerSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 语音通话服务集成测试配置。
 *
 * * [核心策略]
 * - TracingAmqpConsumerSupport：手动 stub，runWithTracing 直接执行 task。
 *   （当 RoomEventConsumer 真实实例被加载时需要此 Bean）
 *
 * @author gopair
 */
@Slf4j
@TestConfiguration
public class VoiceServiceTestConfig {

    @Bean
    public TracingAmqpConsumerSupport tracingAmqpConsumerSupport() {
        return new TracingAmqpConsumerSupportStub();
    }

    private static class TracingAmqpConsumerSupportStub extends TracingAmqpConsumerSupport {
        @Override
        public void runWithTracing(org.springframework.amqp.core.Message message, Runnable task) {
            task.run();
        }
    }
}
