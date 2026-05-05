package com.gopair.roomservice.domain.dto;

import com.gopair.common.entity.BaseQuery;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.validator.constraints.ScriptAssert;

/**
 * 房间DTO
 *
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ScriptAssert(lang = "javascript", script = "_.passwordMode == null || _.passwordMode != 1 || (_.rawPassword != null && _.rawPassword.trim().length() > 0)",
        message = "固定密码模式必须设置密码")
public class RoomDto extends BaseQuery {

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 房间名称
     */
    @NotBlank(message = "房间名称不能为空")
    @Size(min = 2, max = 50, message = "房间名称需在2-50字符之间")
    private String roomName;

    /**
     * 房间描述
     */
    @Size(max = 200, message = "房间描述最多200字符")
    private String description;

    /**
     * 最大成员数（默认10人）
     */
    @Min(value = 2, message = "最大成员数最少为2人")
    @Max(value = 1000, message = "最大成员数最多为1000人")
    private Integer maxMembers = 10;

    /**
     * 房间过期小时数（默认24小时）
     */
    @Min(value = 1, message = "过期时间最少为1小时")
    @Max(value = 168, message = "过期时间最多为168小时（7天）")
    private Integer expireHours;

    /**
     * 密码模式（0-关闭 1-固定密码 2-动态令牌，默认0）
     */
    @Min(value = 0, message = "密码模式值无效")
    @Max(value = 2, message = "密码模式值无效")
    private Integer passwordMode = 0;

    /**
     * 明文密码（仅 passwordMode=1 时必填，条件校验在 Service 层处理）
     */
    @Size(min = 4, max = 20, message = "房间密码需在4-20字符之间")
    private String rawPassword;

    /**
     * 密码是否展示给成员查看（0-隐藏 1-显示，默认1）
     */
    @Min(value = 0, message = "密码可见性值无效")
    @Max(value = 1, message = "密码可见性值无效")
    private Integer passwordVisible;
} 