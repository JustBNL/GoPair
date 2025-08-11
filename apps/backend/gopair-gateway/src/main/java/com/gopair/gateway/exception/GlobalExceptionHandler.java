package com.gopair.gateway.exception;

import com.gopair.common.core.R;
import com.gopair.common.enums.impl.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局异常处理器 (Spring WebFlux)
 * 
 * 处理网关服务中的各种异常，直接构建错误响应
 * 
 * @author gopair
 */
@Slf4j
@Order(-1)
@Component("gatewayGlobalExceptionHandler")
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 设置响应头
        response.getHeaders().add("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        // 确定HTTP状态码
        HttpStatus httpStatus = determineHttpStatus(ex);
        response.setStatusCode(httpStatus);
        
        // 构建错误响应
        R<Void> errorResponse = buildErrorResponse(ex);
        log.error("网关异常: {}", ex.getMessage(), ex);

        // 手动构建JSON响应
        String responseBody = String.format(
            "{\"code\":%d,\"message\":\"%s\",\"data\":null,\"success\":false}",
            errorResponse.getCode(),
            errorResponse.getMsg().replace("\"", "\\\"")
        );
        
        DataBuffer buffer = response.bufferFactory().wrap(responseBody.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Flux.just(buffer));
    }

    /**
     * 构建错误响应
     */
    private R<Void> buildErrorResponse(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            ResponseStatusException rse = (ResponseStatusException) ex;
            return R.fail(CommonErrorCode.BUSINESS_ERROR, rse.getReason());
        } else if (ex instanceof org.springframework.cloud.gateway.support.NotFoundException) {
            return R.fail(CommonErrorCode.RESOURCE_NOT_FOUND, "服务未找到");
        } else if (ex instanceof org.springframework.web.server.ServerWebInputException) {
            return R.fail(CommonErrorCode.PARAM_ERROR, "请求参数错误");
        } else if (ex instanceof java.net.ConnectException) {
            return R.fail(CommonErrorCode.SERVICE_UNAVAILABLE, "服务不可用");
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            return R.fail(CommonErrorCode.SERVICE_UNAVAILABLE, "服务超时");
        }
        
        return R.fail(CommonErrorCode.SYSTEM_ERROR, "系统内部错误");
    }

    /**
     * 确定HTTP状态码
     */
    private HttpStatus determineHttpStatus(Throwable ex) {
        if (ex instanceof ResponseStatusException) {
            return HttpStatus.valueOf(((ResponseStatusException) ex).getStatusCode().value());
        } else if (ex instanceof org.springframework.cloud.gateway.support.NotFoundException) {
            return HttpStatus.NOT_FOUND;
        } else if (ex instanceof org.springframework.web.server.ServerWebInputException) {
            return HttpStatus.BAD_REQUEST;
        } else if (ex instanceof java.net.ConnectException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        } else if (ex instanceof java.util.concurrent.TimeoutException) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
} 