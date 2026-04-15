package com.gopair.adminservice.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gopair.adminservice.domain.po.AdminAuditLog;
import com.gopair.adminservice.service.AuditLogService;
import com.gopair.common.core.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 审计日志控制器
 */
@Tag(name = "审计日志")
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页查询审计日志")
    @GetMapping("/page")
    public R<Page<AdminAuditLog>> getAuditLogPage(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) Long adminId,
            @RequestParam(required = false) String operation,
            @RequestParam(required = false) String targetType) {
        AuditLogService.AuditLogPageQuery query = new AuditLogService.AuditLogPageQuery(
                pageNum, pageSize, adminId, operation, targetType);
        return R.ok(auditLogService.getAuditLogPage(query));
    }
}
