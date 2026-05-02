package com.gopair.chatservice.domain.po;

import com.baomidou.mybatisplus.annotation.*;
import com.gopair.framework.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 好友申请记录实体。
 *
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("friend_request")
public class FriendRequest extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 申请人用户ID。
     */
    private Long fromUserId;

    /**
     * 被申请人用户ID。
     */
    private Long toUserId;

    /**
     * 状态：0-待处理 1-已同意 2-已拒绝
     */
    private Character status;

    /**
     * 申请附言。
     */
    private String message;
}
