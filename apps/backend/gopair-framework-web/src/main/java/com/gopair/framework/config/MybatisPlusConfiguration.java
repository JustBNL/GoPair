package com.gopair.framework.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.gopair.framework.mybatis.AutoFillMetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * MyBatis-Plus配置类
 * 
 * @author gopair
 */
@Slf4j
@Configuration
public class MybatisPlusConfiguration {

    @PostConstruct
    public void init() {
        log.info("[框架配置] MyBatis Plus配置初始化完成");
    }

    /**
     * 配置MyBatis-Plus拦截器
     * 
     * 注册各种内部拦截器，目前包括：
     * - 乐观锁拦截器：支持@Version注解的乐观锁机制
     * 
     * @return MyBatis-Plus拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        
        // 添加乐观锁拦截器
        // 当实体类字段标注@Version注解时，自动启用乐观锁机制
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        
        return interceptor;
    }

    /**
     * 配置MyBatis-Plus自动填充处理器
     * 
     * 自动填充BaseEntity中的时间审计字段：
     * - INSERT操作：填充createTime和updateTime
     * - UPDATE操作：填充updateTime
     * 
     * @return 自动填充处理器实例
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new AutoFillMetaObjectHandler();
    }
} 