package com.gopair.roomservice.domain.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

/**
 * 更新房间密码可见性请求 DTO
 *
 * @author gopair
 */
@Data
public class UpdatePasswordVisibilityDto {

    /**
     * 密码是否展示给成员查看（0-隐藏 1-显示）
     */
    @NotNull(message = "可见性状态不能为空")
    @Min(value = 0, message = "可见性状态值非法")
    @Max(value = 1, message = "可见性状态值非法")
    private Integer visible;
}
