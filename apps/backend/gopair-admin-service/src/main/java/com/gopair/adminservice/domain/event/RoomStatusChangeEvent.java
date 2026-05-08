package com.gopair.adminservice.domain.event;

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

    private Long roomId;
    private Action action;
    private String reason;
    private Long adminId;
    private Long timestamp;
}
