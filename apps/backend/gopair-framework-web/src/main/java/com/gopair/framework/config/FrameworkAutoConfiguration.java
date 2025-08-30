package com.gopair.framework.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

/**
 * GoPair框架主自动配置类
 * 
 * 作为框架的统一入口，通过@Import组织其他功能配置类
 * 业务模块只需引入framework-web依赖即可，无需手动配置
 * 
 * @author gopair
 */
@Slf4j
@AutoConfiguration
@Import({
    MybatisPlusConfiguration.class,    // 数据层配置
    ContextConfiguration.class,        // 上下文配置  
    LoggingConfiguration.class         // 日志配置
})
@ComponentScan(basePackages = {
    "com.gopair.framework.exception",   // 异常处理器
    "com.gopair.framework.entity"       // 实体基础
})
public class FrameworkAutoConfiguration {
    
    public FrameworkAutoConfiguration() {
        log.info("GoPair Framework Web 自动配置已启动");
    }
} 