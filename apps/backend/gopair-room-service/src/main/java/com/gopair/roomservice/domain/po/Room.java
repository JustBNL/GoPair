package com.gopair.roomservice.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 房间实体类
 * 
 * 对应数据库room表
 * 
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("room")
public class Room extends BaseEntity {

    /**
     * 房间ID
     */
    @TableId(type = IdType.AUTO)
    private Long roomId;

    /**
     * 房间邀请码
     */
    private String roomCode;

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
     * 当前成员数
     */
    private Integer currentMembers;

    /**
     * 房主用户ID
     */
    private Long ownerId;

    /**
     * 房间状态（0-活跃 1-已关闭）
     */
    private Integer status;

    /**
     * 房间过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 乐观锁版本（预留字段）
     * 
     * 当前未启用乐观锁机制，如需启用请添加@Version注解
     * 配置的乐观锁拦截器已就绪，可随时激活使用
     */
    private Integer version;
} 