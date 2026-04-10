package com.gopair.framework.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体类基类
 *
 * 所有持久化对象的基类，包含通用的时间审计字段。
 *
 * <p>Jackson 序列化说明：
 * - createTime/updateTime 上的 @JsonFormat 注解具有最高优先级，确保数据库时间始终以易读格式输出。
 * - 对于 BaseEntity 之外的其他 DTO/POJO，全局 Jackson 配置（JacksonConfiguration）提供统一的默认行为兜底：
 *   所有 LocalDateTime 字段默认序列化为 "yyyy-MM-dd HH:mm:ss" 格式，与此基类保持一致。
 *
 * @author gopair
 */
@Data
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
} 