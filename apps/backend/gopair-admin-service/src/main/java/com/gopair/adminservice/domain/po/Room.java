package com.gopair.adminservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 房间实体类，对应数据库room表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("room")
public class Room extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long roomId;

    private String roomCode;

    private String roomName;

    private String description;

    private Integer maxMembers;

    private Integer currentMembers;

    private Long ownerId;

    private Integer status;

    private LocalDateTime expireTime;

    private Integer version;

    private Integer passwordMode;

    private String passwordHash;

    private Integer passwordVisible;
}
