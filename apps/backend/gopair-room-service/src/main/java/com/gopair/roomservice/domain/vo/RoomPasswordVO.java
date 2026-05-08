package com.gopair.roomservice.domain.vo;

import lombok.Data;

/**
 * 房间密码视图对象，仅用于查询当前密码/令牌场景。
 * 与通用 RoomVO 分离，避免密码专用字段污染通用视图。
 *
 * @author gopair
 */
@Data
public class RoomPasswordVO {

    /**
     * 房间ID
     */
    private Long roomId;

    /**
     * 密码模式：0-关闭 1-固定密码 2-动态令牌
     */
    private Integer passwordMode;

    /**
     * 密码是否对成员可见：0-隐藏 1-显示
     */
    private Integer passwordVisible;

    /**
     * 当前密码明文（仅房主始终可查，成员仅在 passwordVisible=1 时可查）
     */
    private String currentPassword;

    /**
     * TOTP 模式下的剩余有效秒数
     */
    private Integer remainingSeconds;
}
