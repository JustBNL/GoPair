package com.gopair.websocketservice.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 订阅数据的 Redis 存储格式（替代 Map&lt;String, Object&gt;）。
 *
 * eventTypes 显式声明为 Set&lt;String&gt;，Jackson 的 DefaultTyping 会自动嵌入类型信息，
 * 恢复时无需 cast 为 Map 再手动提取字段，彻底消除 ClassCastException 风险。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionData {

    private Set<String> eventTypes;
    private String source;
    private LocalDateTime subscribeTime;
    private Integer priority;
}
