package com.gopair.userservice.config;

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
 * 测试专用配置：提供两个 RestTemplate bean，满足不同场景需求。
 *
 * * [核心策略]
 *   - mockRestTemplate（@Primary）：用于 Service 层调用外部服务（file-service），
 *     拦截外部请求并返回预设 stub，不走真实网络。
 *   - realRestTemplate（无 @Primary）：用于测试代码向 localhost 发送 Controller 请求，
 *     不经过任何拦截，走真实网络连接。
 *
 * 使用方式：
 * 在测试类上添加 @Import(MockRestTemplateConfig.class)，
 * Service 会自动注入 mockRestTemplate，测试代码可使用 realRestTemplate。
 */
@TestConfiguration
public class MockRestTemplateConfig {

    @Bean
    @Primary
    public RestTemplate mockRestTemplate() {
        RestTemplate restTemplate = new RestTemplate(new SimpleClientHttpRequestFactory());
        restTemplate.getInterceptors().add(new ConfigurableMockInterceptor());
        return restTemplate;
    }

    @Bean
    public RestTemplate realRestTemplate() {
        return new RestTemplate();
    }

    private static class ConfigurableMockInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(
                org.springframework.http.HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {
            String url = request.getURI().toString();

            String httpStub = HTTP_STUBS.get(url);
            if (httpStub != null) {
                return new MockClientHttpResponse(httpStub);
            }

            // 未配置的外部服务 URL 返回 200 OK + 空成功响应（模拟服务不可用时的降级）
            return new MockClientHttpResponse("{\"code\":200,\"success\":true}");
        }
    }

    public static class MockClientHttpResponse implements ClientHttpResponse {
        private final int statusCode;
        private final String body;

        public MockClientHttpResponse(String responseBody) {
            this.statusCode = 200;
            this.body = responseBody;
        }

        @Override
        public org.springframework.http.HttpStatus getStatusCode() {
            return org.springframework.http.HttpStatus.OK;
        }

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
            return new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final Map<String, String> HTTP_STUBS = new HashMap<>();

    public static void putHttpStub(String url, String responseBody) {
        HTTP_STUBS.put(url, responseBody);
    }

    public static void clear() {
        HTTP_STUBS.clear();
    }

    public static void clearUrlContaining(String fragment) {
        HTTP_STUBS.keySet().removeIf(url -> url.contains(fragment));
    }

    public static void putFileServiceDeleteAvatarStub(Long userId) {
        String url = "http://file-service/file/by-key?objectKey=avatar/" + userId + "/profile.jpg";
        HTTP_STUBS.put(url, "{\"code\":200,\"success\":true}");
    }

    public static void putFileServiceDeleteAvatarStubs(Long userId) {
        putFileServiceDeleteAvatarStub(userId);
        String originalUrl = "http://file-service/file/by-key?objectKey=avatar/" + userId + "/original.jpg";
        HTTP_STUBS.put(originalUrl, "{\"code\":200,\"success\":true}");
    }
}
