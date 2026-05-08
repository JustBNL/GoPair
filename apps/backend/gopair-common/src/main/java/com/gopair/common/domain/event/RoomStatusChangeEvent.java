package com.gopair.common.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 房间状态变更事件（禁用/解禁），由 admin-service 发布，room-service 消费。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomStatusChangeEvent implements Serializable {

    public enum Action {
        DISABLE,
        ENABLE
    }

    /** 房间ID */
    private Long roomId;

    /** 变更动作 */
    private Action action;

    /** 禁用原因（仅 action=DISABLE 时有值） */
    private String reason;

    /** 操作的管理员ID */
    private Long adminId;

    /** 事件时间戳 */
    private Long timestamp;
}
