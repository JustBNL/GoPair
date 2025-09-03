package com.gopair.websocketservice.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.websocketservice.enums.WebSocketErrorCode;
import com.gopair.websocketservice.protocol.MessageType;
import com.gopair.websocketservice.protocol.UnifiedWebSocketMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * WebSocket错误处理器
 * 专门负责统一的错误处理和错误响应
 * 
 * @author gopair
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorHandler {

    private final ObjectMapper objectMapper;

    /**
     * 处理传输错误
     * 
     * @param session WebSocket会话
     * @param exception 异常信息
     */
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        try {
            log.error("[错误处理] WebSocket传输错误: sessionId={}, error={}", 
                    session.getId(), exception.getMessage(), exception);
            
            // 如果会话仍然打开，尝试发送错误消息
            if (session.isOpen()) {
                sendErrorMessage(session, WebSocketErrorCode.CONNECTION_ESTABLISH_FAILED, 
                        "传输错误: " + exception.getMessage());
            }
            
        } catch (Exception e) {
            log.error("[错误处理] 处理传输错误时发生异常: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 发送错误消息并关闭连接
     * 
     * @param session WebSocket会话
     * @param errorCode 错误代码
     * @param errorMessage 错误消息
     */
    public void sendErrorAndClose(WebSocketSession session, WebSocketErrorCode errorCode, String errorMessage) {
        try {
            log.warn("[错误处理] 发送错误并关闭连接: sessionId={}, errorCode={}, message={}", 
                    session.getId(), errorCode, errorMessage);
            
            if (session.isOpen()) {
                sendErrorMessage(session, errorCode, errorMessage);
                
                // 延迟关闭，确保错误消息发送完成
                Thread.sleep(100);
                session.close(CloseStatus.NOT_ACCEPTABLE);
            }
            
        } catch (Exception e) {
            log.error("[错误处理] 发送错误消息失败: sessionId={}", session.getId(), e);
            
            // 强制关闭连接
            try {
                if (session.isOpen()) {
                    session.close(CloseStatus.SERVER_ERROR);
                }
            } catch (Exception closeException) {
                log.error("[错误处理] 强制关闭连接失败: sessionId={}", session.getId(), closeException);
            }
        }
    }

    /**
     * 发送错误消息
     * 
     * @param session WebSocket会话
     * @param errorCode 错误代码
     * @param errorMessage 错误消息
     */
    public void sendErrorMessage(WebSocketSession session, WebSocketErrorCode errorCode, String errorMessage) {
        try {
            if (!session.isOpen()) {
                log.warn("[错误处理] 会话已关闭，无法发送错误消息: sessionId={}", session.getId());
                return;
            }
            
            UnifiedWebSocketMessage errorResponse = new UnifiedWebSocketMessage()
                    .setMessageId(UUID.randomUUID().toString())
                    .setTimestamp(LocalDateTime.now())
                    .setType(MessageType.ERROR)
                    .setEventType("error")
                    .setPayload(Map.of(
                            "errorCode", errorCode.getCode(),
                            "errorMessage", errorMessage,
                            "timestamp", LocalDateTime.now()
                    ));

            String errorJson = objectMapper.writeValueAsString(errorResponse);
            session.sendMessage(new TextMessage(errorJson));
            
            log.debug("[错误处理] 错误消息发送成功: sessionId={}, errorCode={}", 
                    session.getId(), errorCode);
            
        } catch (Exception e) {
            log.error("[错误处理] 发送错误消息异常: sessionId={}, errorCode={}", 
                    session.getId(), errorCode, e);
        }
    }

    /**
     * 处理消息处理错误
     * 
     * @param session WebSocket会话
     * @param originalMessage 原始消息
     * @param exception 异常信息
     */
    public void handleMessageProcessingError(WebSocketSession session, String originalMessage, Exception exception) {
        try {
            log.error("[错误处理] 消息处理错误: sessionId={}, message={}, error={}", 
                    session.getId(), originalMessage, exception.getMessage(), exception);
            
            sendErrorMessage(session, WebSocketErrorCode.MESSAGE_PROCESSING_ERROR, 
                    "消息处理失败: " + exception.getMessage());
            
        } catch (Exception e) {
            log.error("[错误处理] 处理消息处理错误时发生异常: sessionId={}", session.getId(), e);
        }
    }

    /**
     * 处理权限验证错误
     * 
     * @param session WebSocket会话
     * @param action 执行的动作
     * @param reason 失败原因
     */
    public void handlePermissionError(WebSocketSession session, String action, String reason) {
        try {
            log.warn("[错误处理] 权限验证失败: sessionId={}, action={}, reason={}", 
                    session.getId(), action, reason);
            
            sendErrorMessage(session, WebSocketErrorCode.USER_INFO_HEADER_MISSING, 
                    String.format("权限不足，无法执行 %s: %s", action, reason));
            
        } catch (Exception e) {
            log.error("[错误处理] 处理权限错误时发生异常: sessionId={}", session.getId(), e);
        }
    }
} 