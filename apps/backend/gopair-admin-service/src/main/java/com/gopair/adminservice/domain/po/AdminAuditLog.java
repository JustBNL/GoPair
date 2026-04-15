package com.gopair.adminservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作审计日志实体，对应数据库admin_audit_log表
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;

    private String adminUsername;

    private String operation;

    private String targetType;

    private String targetId;

    private String detail;

    private String ipAddress;

    private String userAgent;

    private LocalDateTime createTime;
}
