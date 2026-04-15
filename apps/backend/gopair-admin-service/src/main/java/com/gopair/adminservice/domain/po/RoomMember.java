package com.gopair.adminservice.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 房间成员实体类，对应数据库room_member表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("room_member")
public class RoomMember extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Long userId;

    private Integer role;

    private Integer status;

    private LocalDateTime joinTime;

    private LocalDateTime lastActiveTime;
}
