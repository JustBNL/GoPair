package com.gopair.adminservice.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gopair.adminservice.annotation.AdminAudit;
import com.gopair.adminservice.context.AdminContext;
import com.gopair.adminservice.context.AdminContextHolder;
import com.gopair.adminservice.domain.po.AdminAuditLog;
import com.gopair.adminservice.mapper.AdminAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理员操作审计切面，拦截所有标注了 @AdminAudit 的方法并同步记录审计日志。
 */
@Slf4j
@Aspect
@Component
public class AdminAuditAspect {

    private final AdminAuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public AdminAuditAspect(
            AdminAuditLogMapper auditLogMapper,
            ObjectMapper objectMapper) {
        this.auditLogMapper = auditLogMapper;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(adminAudit)")
    public Object around(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) throws Throwable {
        Object result = joinPoint.proceed();
        writeLog(adminAudit, joinPoint, result);
        return result;
    }

    private void writeLog(AdminAudit adminAudit, ProceedingJoinPoint joinPoint, Object result) {
        try {
            AdminContext context = AdminContextHolder.get();
            if (context == null) {
                log.warn("[AdminAudit] 无法获取管理员上下文，跳过审计日志记录");
                return;
            }

            AdminAuditLog auditLog = new AdminAuditLog();
            auditLog.setAdminId(context.getAdminId());
            auditLog.setAdminUsername(context.getUsername());
            auditLog.setOperation(adminAudit.operation());
            auditLog.setTargetType(adminAudit.targetType());
            auditLog.setTargetId(extractTargetId(joinPoint));
            auditLog.setDetail(buildDetail(joinPoint, result));
            auditLog.setCreateTime(LocalDateTime.now());

            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                auditLog.setIpAddress(getClientIp(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }

            auditLogMapper.insert(auditLog);
            log.info("[AdminAudit] 审计日志记录成功: operation={}, targetType={}, targetId={}, admin={}",
                    adminAudit.operation(), adminAudit.targetType(), auditLog.getTargetId(), context.getUsername());
        } catch (Exception e) {
            log.error("[AdminAudit] 审计日志记录失败", e);
        }
    }

    private String extractTargetId(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();

        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName();
            if (name.equals("userId") || name.equals("roomId") || name.equals("fileId")
                    || name.equals("messageId") || name.equals("callId")) {
                if (args[i] != null) {
                    return args[i].toString();
                }
            }
        }
        return "";
    }

    private String buildDetail(ProceedingJoinPoint joinPoint, Object result) {
        try {
            Map<String, Object> detail = new HashMap<>();
            detail.put("method", joinPoint.getSignature().getName());
            detail.put("args", joinPoint.getArgs());
            detail.put("result", result != null ? result.toString() : null);
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        if (ip != null && (ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1") || ip.equals("127.0.0.1"))) {
            try {
                ip = java.net.InetAddress.getLocalHost().getHostAddress();
            } catch (Exception e) {
                ip = "LOCAL_DEV";
            }
        }
        return ip;
    }
}
