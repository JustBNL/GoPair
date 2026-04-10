package com.gopair.framework.config;

import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Jackson 全局序列化配置
 *
 * 为所有 Jackson ObjectMapper 提供一致的默认行为，作为字段级注解的兜底。
 *
 * <p>配置内容：
 * <ul>
 *   <li>Java 8 时间类型（LocalDateTime 等）序列化：yyyy-MM-dd HH:mm:ss，与 BaseEntity.@JsonFormat 保持一致</li>
 *   <li>空对象序列化失败：关闭（避免内部 API 序列化空对象时抛异常）</li>
 * </ul>
 *
 * <p>字段级 @JsonFormat 注解优先于此全局配置（如 BaseEntity.createTime/updateTime）。
 *
 * @author gopair
 */
@Slf4j
@AutoConfiguration
public class JacksonConfiguration {

    /**
     * Jackson 全局定制器，统一配置 ObjectMapper 的序列化行为。
     *
     * <p>技术说明：
     * - 返回 Jackson2ObjectMapperBuilderCustomizer，Spring Boot 自动收集并按顺序应用，
     *   与 JacksonAutoConfiguration 的默认定制器协同生效。
     * - 不直接定义 ObjectMapper Bean（由 Spring Boot 统一创建），避免同名 Bean 冲突。
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonGlobalCustomizer() {
        log.info("[框架配置] Jackson 全局序列化配置已加载（BaseEntity @JsonFormat 优先）");

        return builder -> {
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            builder.featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        };
    }
}
