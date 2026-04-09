package com.gopair.roomservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 房间成员实体类
 * 
 * 对应数据库room_member表
 * 
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("room_member")
public class RoomMember extends BaseEntity {

    /**
     * 记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 用户ID
     */
    private Long userId;


    /**
     * 角色（0-普通成员 1-管理员 2-房主）
     */
    private Integer role;

    /**
     * 状态（0-在线 1-离线）
     */
    private Integer status;

    /**
     * 加入时间
     */
    private LocalDateTime joinTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;
} 