package com.gopair.roomservice.domain.dto;

import com.gopair.common.entity.BaseQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 房间DTO
 * 
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RoomDto extends BaseQuery {

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 房间名称
     */
    private String roomName;

    /**
     * 房间描述
     */
    private String description;

    /**
     * 最大成员数
     */
    private Integer maxMembers;

    /**
     * 房间过期小时数（默认24小时）
     */
    private Integer expireHours;

    /**
     * 密码模式（0-关闭 1-固定密码 2-动态令牌）
     */
    private Integer passwordMode;

    /**
     * 明文密码（仅 passwordMode=1 时必填）
     */
    private String rawPassword;

    /**
     * 密码是否展示给成员查看（0-隐藏 1-显示，默认1）
     */
    private Integer passwordVisible;
} 