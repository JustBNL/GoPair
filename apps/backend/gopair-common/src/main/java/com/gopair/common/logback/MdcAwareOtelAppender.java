package com.gopair.common.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.gopair.common.constants.SystemConstants;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import org.slf4j.MDC;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MDC 感知型 OpenTelemetry Logback Appender
 *
 * 继承 OpenTelemetryAppender，重写 append 逻辑。
 * 在 append() 调用线程（原始请求线程）中从 MDC 提取 traceId/userId/nickname，
 * 通过 OTel Logs API 同步发送，保证结构化 attributes 完整注入。
 *
 * * [核心策略]
 * - 延迟初始化：不在 Logback 初始化阶段调用 GlobalOpenTelemetry.get()，
 *   避免触发 auto-configure 设置 noop，阻止后续 SDK Bean 的 set() 注册。
 *   首次 append 时才尝试获取 SDK；若 SDK 未就绪则静默降级，下次 append 重试。
 * - 同步发送：直接调用 OTel Logger API，不走父类异步队列，零资源浪费
 * - MDC 读取发生在 append() 调用线程，恰好是原始请求线程，MDC 完整
 * - OTel SpanContext 由 GlobalOpenTelemetry 自动注入 LogRecord
 * - 降级兜底：若 GlobalOpenTelemetry 未初始化，静默跳过不抛异常，不污染业务线程
 *
 * * [执行链路]
 * 1. Logback 调用 append()，传入 ILoggingEvent（此时仍是原始请求线程，MDC 完整）
 * 2. 首次调用：尝试从 GlobalOpenTelemetry 获取 Logger（延迟获取，避免 bootstrap 冲突）
 *    - 若 SDK 已就绪（SDK Bean 先于首次日志执行），正常获取 Logger 并缓存
 *    - 若 SDK 未就绪（Logback 日志早于 SDK Bean），静默降级，logger=null，不抛异常
 * 3. 后续调用：logger 已缓存，直接使用，跳过初始化逻辑
 * 4. 提取 MDC 中的 traceId / userId / nickname 及线程信息
 * 5. 构造 LogRecord 并同步 emit
 *
 * @author gopair
 */
public class MdcAwareOtelAppender extends io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender {

    private static final AttributeKey<String> KEY_TRACE_ID = AttributeKey.stringKey("traceId");
    private static final AttributeKey<String> KEY_USER_ID = AttributeKey.stringKey("userId");
    private static final AttributeKey<String> KEY_NICKNAME = AttributeKey.stringKey("nickname");
    private static final AttributeKey<String> KEY_THREAD_NAME = AttributeKey.stringKey("thread");
    private static final AttributeKey<String> KEY_LOGGER = AttributeKey.stringKey("logger");

    private volatile Logger logger;
    private volatile boolean initialized;
    private final AtomicBoolean initializing = new AtomicBoolean(false);

    /**
     * 重写 append：不再调用父类异步发送机制（异步线程中 MDC 已丢失），
     * 改为在当前线程（原始请求线程）同步发送，保证 MDC 上下文完整。
     */
    @Override
    public void append(ILoggingEvent event) {
        // 延迟初始化：只在第一次 append 时尝试获取 SDK，避免 Logback 初始化阶段就触发
        // GlobalOpenTelemetry.get()（会触发 auto-configure 设置 noop，导致 SDK Bean 的 set() 被拒绝）
        if (!initialized) {
            tryInitLogger();
        }

        if (logger == null) {
            return;
        }

        try {
            String body = event.getMessage();
            long timestamp = event.getTimeStamp();
            Severity severity = toOtelSeverity(event.getLevel());

            io.opentelemetry.api.logs.LogRecordBuilder builder = logger.logRecordBuilder()
                    .setBody(body)
                    .setTimestamp(timestamp, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .setSeverity(severity)
                    .setSeverityText(event.getLevel().toString());

            AttributesBuilder attrBuilder = Attributes.builder();

            // 注入 MDC 上下文（关键：此处在原始请求线程，MDC 完整）
            String traceId = MDC.get(SystemConstants.MDC_TRACE_ID);
            if (traceId != null && !traceId.isEmpty()) {
                attrBuilder.put(KEY_TRACE_ID, traceId);
            }
            String userId = MDC.get(SystemConstants.MDC_USER_ID);
            if (userId != null && !userId.isEmpty()) {
                attrBuilder.put(KEY_USER_ID, userId);
            }
            String nickname = MDC.get(SystemConstants.MDC_NICKNAME);
            if (nickname != null && !nickname.isEmpty()) {
                attrBuilder.put(KEY_NICKNAME, nickname);
            }

            // 注入线程名和 logger 名（便于排查）
            attrBuilder.put(KEY_THREAD_NAME, event.getThreadName());
            attrBuilder.put(KEY_LOGGER, event.getLoggerName());

            builder.setAllAttributes(attrBuilder.build());
            builder.emit();

        } catch (Exception e) {
            // 不抛出异常，避免影响业务线程
            System.err.println("[MdcAwareOtelAppender] Failed to emit log: " + e.getMessage());
        }
    }

    /**
     * 延迟初始化 LoggerProvider。
     *
     * 只在第一次 append 时执行（initialized 双重检查锁定）。
     * 此方法在应用启动后的第一次日志时调用，此时 SDK Bean 应已就绪。
     *
     * 注意：
     * - 不得在 Logback 配置阶段（构造函数/start() 方法）调用 GlobalOpenTelemetry.get()，
     *   否则会触发 auto-configure 导致 noop 被设置，阻止 SDK Bean 的 set() 成功注册。
     * - 使用 compareAndSet 保证线程安全初始化。
     */
    private void tryInitLogger() {
        if (initialized || !initializing.compareAndSet(false, true)) {
            return;
        }
        try {
            // 从静态 Holder 获取 SDK，绕过 GlobalOpenTelemetry 的 "先 get 后 set" 限制
            OpenTelemetry otel = OtelSdkHolder.get();
            if (otel != null) {
                logger = otel.getLogsBridge().get("gopair-logger");
            }
        } catch (Throwable t) {
            // 静默降级：若 SDK 未就绪，logger=null，后续 append 跳过日志发送
            this.logger = null;
        } finally {
            initialized = true;
        }
    }

    private Severity toOtelSeverity(ch.qos.logback.classic.Level level) {
        int levelInt = level.toInt();
        if (levelInt < ch.qos.logback.classic.Level.DEBUG_INT) {
            return Severity.TRACE;
        } else if (levelInt < ch.qos.logback.classic.Level.INFO_INT) {
            return Severity.DEBUG;
        } else if (levelInt < ch.qos.logback.classic.Level.WARN_INT) {
            return Severity.INFO;
        } else if (levelInt < ch.qos.logback.classic.Level.ERROR_INT) {
            return Severity.WARN;
        } else {
            return Severity.ERROR;
        }
    }
}
