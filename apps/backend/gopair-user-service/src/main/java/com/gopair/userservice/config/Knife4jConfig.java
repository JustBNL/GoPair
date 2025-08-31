package com.gopair.userservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 用户服务Knife4j配置类
 * 
 * 配置API文档和JWT认证支持
 * 
 * @author gopair
 */
@Slf4j
@Configuration
public class Knife4jConfig {

    @PostConstruct
    public void init() {
        log.info("[用户服务] Knife4j API文档配置初始化完成");
    }

    /**
     * 创建API分组 - 用户服务
     */
    @Bean
    public GroupedOpenApi userApi() {
        return GroupedOpenApi.builder()
                .group("用户服务")
                .pathsToMatch("/user/**")
                .build();
    }

    /**
     * 创建OpenAPI配置
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .name("Authorization")
                                .description("输入JWT Token进行认证"))
                )
                .info(new Info()
                        .title("GoPair - 用户服务API")
                        .description("提供用户注册、登录及管理等核心功能。")
                        .version("v1.0.0")
                );
    }
} 