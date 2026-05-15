package com.gopair.messageservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 测试专用配置：提供两个 RestTemplate bean，满足不同场景需求
 *
 * * [核心策略]
 *   - mockRestTemplate（@Primary）：用于 Service 层调用外部服务（room-service/user-service），
 *     通过拦截器返回预设的 stub 数据，实现外部依赖的 Mock。
 *     默认行为：未配置的 room-service URL 返回 data=false，user-service URL 返回默认响应。
 *   - realRestTemplate（无 @Primary）：用于测试代码向 localhost 发送 Controller 请求，
 *     不经过任何拦截，走真实网络连接。
 *   - @Qualifier("mockRestTemplate") / @Qualifier("realRestTemplate") 区分注入目标。
 *
 * * [拦截逻辑]
 *   - localhost/* → 跳过拦截，走真实网络连接（Controller 测试的 HTTP 请求）
 *   - room-service/* → 查找 BOOL_STUBS 精确匹配，未配置则返回 data=false
 *   - user-service/* → 查找 HTTP_STUBS 精确匹配，未配置则返回默认响应
 *
 * 使用方式：
 * <pre>
 * // Service 层（自动注入 mockRestTemplate）：
 * public MyService(RestTemplate restTemplate) { ... }
 *
 * // 测试代码（显式注入 realRestTemplate）：
 * public class MyTest {
 *     @Qualifier("realRestTemplate")
 *     @Autowired
 *     private RestTemplate realRestTemplate;
 * }
 *
 * // 配置 stub：
 * MockRestTemplateConfig.putStub("http://room-service/room/1/members/100/check", true);
 * MockRestTemplateConfig.clear();
 * </pre>
 */
@TestConfiguration
public class MockRestTemplateConfig {

    /**
     * Mock RestTemplate（@Primary）：用于 Service 层调用外部服务（room-service/user-service），
     * 通过拦截器返回预设的 stub 数据，实现外部依赖的 Mock。
     * @Primary 确保在没有显式 @Qualifier 时优先注入此 mock 实例。
     */
    @Bean
    @Primary
    public RestTemplate mockRestTemplate() {
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        restTemplate.getInterceptors().add(new ConfigurableMockInterceptor());
        return restTemplate;
    }

    /**
     * Real RestTemplate：无拦截器，用于测试代码向 localhost 发送 Controller 请求，
     * 走真实网络连接，确保 Controller 请求能正确到达应用。
     */
    @Bean
    public RestTemplate realRestTemplate() {
        return new RestTemplate();
    }

    /**
     * 可配置的拦截器。
     *
     * 拦截策略（按优先级）：
     * 1. localhost/* → 跳过，走真实网络连接
     * 2. room-service/* → 查找 BOOL_STUBS 精确匹配，未配置返回 data=false
     * 3. user-service/* → 查找 HTTP_STUBS 精确匹配，未配置返回默认响应
     */
    public static class ConfigurableMockInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {

            String url = request.getURI().toString();

            // localhost 请求（Controller 测试向 localhost 发起的 HTTP 调用）-> 跳过拦截，走真实连接
            if (url.contains("localhost")) {
                return execution.execute(request, body);
            }

            // room-service 请求：查找 BOOL_STUBS 精确匹配
            if (url.contains("room-service")) {
                // 精确匹配
                Boolean boolStub = BOOL_STUBS.get(url);
                if (boolStub != null) {
                    return new MockClientHttpResponse(boolStub);
                }
                // URL 模式匹配（支持 http://room-service/room/{id}/members/{uid}/check）
                for (Map.Entry<String, Boolean> entry : BOOL_STUBS.entrySet()) {
                    String stubUrl = entry.getKey();
                    String pattern = stubUrl.replaceFirst("http://[^/]+", "");
                    if (url.contains(pattern)) {
                        return new MockClientHttpResponse(entry.getValue());
                    }
                }
                // 未配置 stub 的 room-service URL：返回默认 data=false
                // 模拟"服务不可用或用户不在房间"的降级行为
                return new MockClientHttpResponse(false);
            }

            // user-service 请求：查找 HTTP_STUBS 精确匹配
            String httpStub = HTTP_STUBS.get(url);
            if (httpStub != null) {
                return new MockClientHttpResponse(httpStub);
            }
            // 未配置的 user-service URL：返回默认 data=false
            return new MockClientHttpResponse(false);
        }
    }

    /**
     * mock HTTP 响应封装（支持布尔值和字符串响应体）
     */
    public static class MockClientHttpResponse implements ClientHttpResponse {

        private final int statusCode;
        private final String body;

        public MockClientHttpResponse(boolean boolValue) {
            this.statusCode = 200;
            this.body = "{\"code\":200,\"data\":" + boolValue + "}";
        }

        public MockClientHttpResponse(String responseBody) {
            this.statusCode = 200;
            this.body = responseBody;
        }

        @Override
        public org.springframework.http.HttpStatus getStatusCode() {
            return org.springframework.http.HttpStatus.OK;
        }

        @SuppressWarnings("deprecation")
        @Override
        public int getRawStatusCode() {
            return statusCode;
        }

        @Override
        public String getStatusText() {
            return "";
        }

        @Override
        public void close() {}

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return headers;
        }

        @Override
        public java.io.InputStream getBody() throws IOException {
            return new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * stub 存储：key=完整 URL，value=mock 响应（布尔值）
     */
    private static final Map<String, Boolean> BOOL_STUBS = new HashMap<>();

    /**
     * stub 存储：key=完整 URL，value=mock HTTP 响应体（字符串）
     */
    private static final Map<String, String> HTTP_STUBS = new HashMap<>();

    /**
     * 配置指定 URL 的 stub 响应（布尔值，用于房间成员检查）
     */
    public static void putStub(String url, boolean isMember) {
        BOOL_STUBS.put(url, isMember);
    }

    /**
     * 配置指定 URL 的 stub HTTP 响应体（字符串，用于 user-service HTTP 降级）
     */
    public static void putHttpStub(String url, String responseBody) {
        HTTP_STUBS.put(url, responseBody);
    }

    /**
     * 清除所有 stub 配置
     */
    public static void clear() {
        BOOL_STUBS.clear();
        HTTP_STUBS.clear();
    }

    /**
     * 批量清除包含指定片段的 stub
     */
    public static void clearUrlContaining(String fragment) {
        BOOL_STUBS.keySet().removeIf(url -> url.contains(fragment));
        HTTP_STUBS.keySet().removeIf(url -> url.contains(fragment));
    }

    /**
     * 获取当前所有 stub（用于调试）
     */
    public static Map<String, ?> getStubs() {
        return Map.of("BOOL_STUBS", Map.copyOf(BOOL_STUBS), "HTTP_STUBS", Map.copyOf(HTTP_STUBS));
    }
}
