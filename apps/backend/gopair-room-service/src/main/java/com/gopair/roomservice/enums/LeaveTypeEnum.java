package com.gopair.roomservice.enums;

/**
 * 成员离开类型枚举。
 *
 * <p>用于 {@link com.gopair.roomservice.domain.event.MemberRemovalEvent#leaveType} 字段，
 * 替代原有的 Integer 类型，消除硬编码数字 1/2/3 的歧义。
 */
public enum LeaveTypeEnum {

    /** 主动离开 — 用户自行退出房间 */
    VOLUNTARY(1),

    /** 被踢出 — 房主将成员移除房间 */
    KICKED(2),

    /** 房间关闭被动离开 — 房主关闭房间导致全员被动退出 */
    ROOM_CLOSED(3);

    private final int value;

    LeaveTypeEnum(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static LeaveTypeEnum fromValue(int value) {
        for (LeaveTypeEnum e : values()) {
            if (e.value == value) {
                return e;
            }
        }
        throw new IllegalArgumentException("未知离开类型值: " + value);
    }
}
