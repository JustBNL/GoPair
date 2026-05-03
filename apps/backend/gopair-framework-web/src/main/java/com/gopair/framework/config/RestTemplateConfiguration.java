package com.gopair.framework.config;

import com.gopair.common.constants.SystemConstants;
import com.gopair.framework.context.UserContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;

/**
 * RestTemplate 自动配置
 *
 * * [核心策略]
 * - 仅在 RestTemplate 类存在时加载，适配非 Web 服务不引入该 Bean。
 * - 配合 @LoadBalanced 支持通过 Nacos 服务名（如 user-service）进行服务间 HTTP 调用。
 * - 用户上下文转发：自动将当前请求的 userId/nickname 通过 HTTP Header 传递到下游服务，
 *   保证服务间调用时 ContextInitFilter 能正确解析用户身份。
 * - 同时转发 Authorization header，使下游服务能验证当前登录用户身份。
 *
 * @author gopair
 */
@Configuration
@ConditionalOnClass(RestTemplate.class)
@ConditionalOnProperty(name = "gopair.rest-template.enabled", havingValue = "true", matchIfMissing = true)
public class RestTemplateConfiguration {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(new UserContextPropagationInterceptor());
        return restTemplate;
    }

    /**
     * 服务间 HTTP 调用用户上下文转发拦截器。
     * 将当前线程的 userId/nickname 和 Authorization header 注入下游请求头，
     * 供下游服务的 ContextInitFilter 正确解析用户身份。
     */
    private static class UserContextPropagationInterceptor implements ClientHttpRequestInterceptor {
        @Override
        public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                            ClientHttpRequestExecution execution) throws IOException {
            Long userId = UserContextHolder.getCurrentUserId();
            String nickname = UserContextHolder.getCurrentNickname();
            if (userId != null) {
                request.getHeaders().add(SystemConstants.HEADER_USER_ID, String.valueOf(userId));
            }
            if (nickname != null) {
                request.getHeaders().add(SystemConstants.HEADER_NICKNAME, nickname);
            }
            // 转发 Authorization header，使下游服务能验证当前登录用户身份
            try {
                HttpServletRequest servletRequest = (HttpServletRequest) RequestContextHolder.getRequestAttributes()
                        .resolveReference(null);
                if (servletRequest != null) {
                    String authHeader = servletRequest.getHeader(SystemConstants.AUTHORIZATION_HEADER);
                    if (authHeader != null && !authHeader.isBlank()) {
                        request.getHeaders().add(SystemConstants.AUTHORIZATION_HEADER, authHeader);
                    }
                }
            } catch (Exception ignored) {
                // 非 Web 环境（如单元测试）可能没有 RequestContextHolder，降级忽略
            }
            return execution.execute(request, body);
        }
    }
}
