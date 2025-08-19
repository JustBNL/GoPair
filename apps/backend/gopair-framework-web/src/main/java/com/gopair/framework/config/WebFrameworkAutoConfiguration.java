package com.gopair.framework.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Web框架自动配置类
 * 
 * 基于Spring Boot 3.x自动配置标准，自动扫描并注册Web框架中的所有组件
 * 业务模块只需引入framework-web依赖即可，无需手动配置
 * 
 * @author gopair
 */
@AutoConfiguration
@ComponentScan(basePackages = {
    "com.gopair.framework.config",      // 配置类
    "com.gopair.framework.exception",   // 异常处理器
    "com.gopair.framework.validation",  // 验证组件
    "com.gopair.framework.entity"       // 实体基础（如果有需要Bean化的）
})
public class WebFrameworkAutoConfiguration {
    
} 