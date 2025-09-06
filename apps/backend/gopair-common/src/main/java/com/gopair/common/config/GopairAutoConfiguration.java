package com.gopair.common.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * GoPair 自动配置类
 * 
 * 基于Spring Boot 3.x最新标准，自动扫描并注册公共模块中的所有Bean
 * 业务模块只需引入common依赖即可，无需手动配置ComponentScan
 * 
 * @author gopair
 */
@AutoConfiguration
@ComponentScan(basePackages = {
    "com.gopair.common.config",      // 配置类
    "com.gopair.common.util",        // 工具类
    "com.gopair.common.service"      // 服务类（包含WebSocketMessageProducer）
})
public class GopairAutoConfiguration {
    
}
