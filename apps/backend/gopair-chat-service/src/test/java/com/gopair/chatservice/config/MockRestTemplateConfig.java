package com.gopair.chatservice.config;

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
import java.util.HashMap;
import java.util.Map;

/**
 * 测试专用配置：提供两个 RestTemplate bean。
 *
 * mockRestTemplate（@Primary）：拦截外部服务调用，返回预设 stub。
 * realRestTemplate：用于测试代码向 localhost 发送 HTTP 请求。
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

    public static class ConfigurableMockInterceptor implements ClientHttpRequestInterceptor {
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

            for (Map.Entry<String, String> entry : HTTP_STUBS.entrySet()) {
                if (url.contains(entry.getKey())) {
                    return new MockClientHttpResponse(entry.getValue());
                }
            }

            return new MockClientHttpResponse("{\"code\":500}");
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
        public int getRawStatusCode() { return statusCode; }

        @Override
        public String getStatusText() { return ""; }

        @Override
        public void close() {}

        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            var headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            return headers;
        }

        @Override
        public java.io.InputStream getBody() throws IOException {
            return new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static final Map<String, String> HTTP_STUBS = new HashMap<>();

    public static void putHttpStub(String url, String responseBody) {
        HTTP_STUBS.put(url, responseBody);
    }

    public static void clear() {
        HTTP_STUBS.clear();
    }
}
