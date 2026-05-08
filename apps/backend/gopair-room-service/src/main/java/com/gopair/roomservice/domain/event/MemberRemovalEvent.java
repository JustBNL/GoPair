package com.gopair.roomservice.domain.event;

import com.gopair.roomservice.enums.LeaveTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 成员移除事件，leaveRoom / kickMember / closeRoom 共用。
 *
 * @see com.gopair.roomservice.messaging.MemberRemovalConsumer
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemberRemovalEvent implements Serializable {
    /** 房间ID */
    private Long roomId;
    /** 被移除的用户ID */
    private Long userId;
    /** 离开类型，见 {@link LeaveTypeEnum} */
    private LeaveTypeEnum leaveType;
    /** 操作者ID（踢人时为房主ID，其他为 null） */
    private Long operatorId;
    private String correlationId;
    private Long requestAt;
}
