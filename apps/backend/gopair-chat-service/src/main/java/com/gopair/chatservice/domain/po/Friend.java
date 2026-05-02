package com.gopair.chatservice.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 好友关系实体。
 *
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("friend")
public class Friend extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（较小的一方）。
     * 存入时始终取 min(fromUserId, toUserId)。
     */
    private Long userId;

    /**
     * 好友ID（较大的一方）。
     * 存入时始终取 max(fromUserId, toUserId)。
     */
    private Long friendId;

    /**
     * 好友备注。
     */
    private String remark;

    /**
     * 状态：0-待确认 1-已同意 2-已拒绝
     */
    private Character status;
}
