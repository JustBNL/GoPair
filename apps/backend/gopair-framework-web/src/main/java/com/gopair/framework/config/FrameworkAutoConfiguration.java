package com.gopair.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * GoPair框架主自动配置类
 *
 * @author gopair
 */
@Slf4j
@AutoConfiguration
@Import({
    MybatisPlusConfiguration.class,    // 数据层配置
    ContextConfiguration.class,        // 上下文配置
    LoggingConfiguration.class,        // 日志配置
    JacksonConfiguration.class,        // Jackson 全局序列化配置
    RestTemplateConfiguration.class    // RestTemplate 服务间 HTTP 调用配置
})
@ComponentScan(basePackages = {
    "com.gopair.framework.exception",   // 异常处理器
    "com.gopair.framework.entity"       // 实体基础
})
public class FrameworkAutoConfiguration {

    public FrameworkAutoConfiguration() {
        log.info("[框架配置] Framework Web 模块自动配置已启动");
    }
}
