package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.AdminAuditLog;
import com.gopair.adminservice.mapper.AdminAuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 审计日志查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AdminAuditLogMapper auditLogMapper;

    public record AuditLogPageQuery(
            Integer pageNum,
            Integer pageSize,
            Long adminId,
            String operation,
            String targetType
    ) {}

    public Page<AdminAuditLog> getAuditLogPage(AuditLogPageQuery query) {
        Page<AdminAuditLog> page = new Page<>(query.pageNum(), query.pageSize());
        return auditLogMapper.selectPage(page,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AdminAuditLog>()
                        .eq(query.adminId() != null, AdminAuditLog::getAdminId, query.adminId())
                        .eq(query.operation() != null, AdminAuditLog::getOperation, query.operation())
                        .eq(query.targetType() != null, AdminAuditLog::getTargetType, query.targetType())
                        .orderByDesc(AdminAuditLog::getCreateTime)
        );
    }
}
