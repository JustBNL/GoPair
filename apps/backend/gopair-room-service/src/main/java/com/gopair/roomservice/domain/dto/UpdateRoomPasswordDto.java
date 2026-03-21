package com.gopair.roomservice.domain.dto;

import lombok.Data;

/**
 * 更新房间密码请求 DTO
 *
 * @author gopair
 */
@Data
public class UpdateRoomPasswordDto {

    /**
     * 密码模式（0-关闭 1-固定密码 2-动态令牌）
     */
    private Integer mode;

    /**
     * 明文密码（mode=1 时必填）
     */
    private String rawPassword;

    /**
     * 密码是否展示给成员查看（0-隐藏 1-显示，默认1）
     */
    private Integer visible;
}
