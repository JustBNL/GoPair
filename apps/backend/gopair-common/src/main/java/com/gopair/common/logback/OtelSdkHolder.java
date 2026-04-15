package com.gopair.common.logback;

import io.opentelemetry.api.OpenTelemetry;

/**
 * OTel SDK 静态持有者
 *
 * 用途：
 * - 解决 GlobalOpenTelemetry 的"先 get 后 set" 冲突。
 *   在 OTel 1.35 中，GlobalOpenTelemetry.get() 会无条件调用 set(noop())，导致 SDK Bean 的 set() 被拒绝。
 *   为此我们不使用 GlobalOpenTelemetry，而是通过这个 Holder 在 SDK Bean 和 Appender 之间传递实例。
 *
 * * [执行链路]
 * 1. GatewayOtelConfiguration.openTelemetry() Bean 构建 SDK 后，调用 OtelSdkHolder.set(sdk) 注入
 * 2. MdcAwareOtelAppender.append() 首次调用时，从 OtelSdkHolder.get() 获取 SDK 实例
 * 3. 若 SDK 未就绪（append 早于 Bean），logger=null，静默降级，后续 append 重试
 *
 * @author gopair
 */
public class OtelSdkHolder {

    private static volatile OpenTelemetry instance;

    private OtelSdkHolder() {
    }

    public static void set(OpenTelemetry otel) {
        OtelSdkHolder.instance = otel;
    }

    public static OpenTelemetry get() {
        return instance;
    }
}
