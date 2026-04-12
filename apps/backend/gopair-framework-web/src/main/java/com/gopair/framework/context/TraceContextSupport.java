package com.gopair.framework.context;

import brave.Tracer;
import brave.baggage.BaggageField;
import com.gopair.common.constants.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

/**
 * 追踪上下文支持组件
 *
 * 封装对 Brave Tracer 的条件性访问，仅在 Tracer Bean 存在时激活。
 * 提供 traceId 读取和用户信息 Baggage 注入能力，
 * 使 ContextInitFilter 不直接依赖 Brave API。
 *
 * 注意：使用 brave.Tracer（Brave 原生）而非 io.micrometer.tracing.Tracer，
 * 确保 BaggageField.updateValue() 接收到正确的 brave.propagation.TraceContext 类型。
 *
 * @author gopair
 */
@Slf4j
@Component
@ConditionalOnBean(Tracer.class)
public class TraceContextSupport {

    private final Tracer tracer;

    private static final BaggageField USER_ID_FIELD = BaggageField.create(SystemConstants.MDC_USER_ID);
    private static final BaggageField NICKNAME_FIELD = BaggageField.create(SystemConstants.MDC_NICKNAME);

    public TraceContextSupport(Tracer tracer) {
        this.tracer = tracer;
        log.info("[追踪支持] TraceContextSupport 初始化完成，Brave Tracer 已就绪");
    }

    /**
     * 从当前 Brave Span 获取 traceId
     *
     * @return traceId 字符串，若无当前 Span 则返回 null
     */
    public String getCurrentTraceId() {
        try {
            brave.Span span = tracer.currentSpan();
            if (span != null) {
                return span.context().traceIdString();
            }
        } catch (Exception e) {
            log.debug("[追踪支持] 获取 Brave traceId 失败", e);
        }
        return null;
    }

    /**
     * 将 userId 和 nickname 注入 Brave BaggageField，并同步写入 MDC
     *
     * BaggageField 注入后，TracingMdcConfiguration 的 MDCScopeDecorator
     * 会在 Servlet 环境下自动将其桥接到 MDC（flushOnUpdate 已配置）。
     * 此处额外显式写入 MDC，作为双保险，确保在桥接时序不稳定时日志仍能正确输出。
     *
     * @param userId   用户 ID 字符串
     * @param nickname 用户昵称
     */
    public void enrichMdcWithUserBaggage(String userId, String nickname) {
        try {
            brave.Span span = tracer.currentSpan();
            if (span != null) {
                if (userId != null) {
                    USER_ID_FIELD.updateValue(span.context(), userId);
                }
                if (nickname != null) {
                    NICKNAME_FIELD.updateValue(span.context(), nickname);
                }
                log.debug("[追踪支持] 已将 userId={}, nickname={} 注入 BaggageField", userId, nickname);
            }
        } catch (Exception e) {
            log.debug("[追踪支持] 注入 BaggageField 失败，降级为纯 MDC 写入", e);
        }
        // 双保险：无论 Baggage 是否成功，都直接写 MDC
        if (userId != null) {
            MDC.put(SystemConstants.MDC_USER_ID, userId);
        }
        if (nickname != null) {
            MDC.put(SystemConstants.MDC_NICKNAME, nickname);
        }
    }
}
