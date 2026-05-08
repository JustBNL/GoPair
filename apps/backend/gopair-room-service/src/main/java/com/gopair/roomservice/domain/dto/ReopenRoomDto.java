package com.gopair.roomservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 房间重新开启请求
 *
 * @author gopair
 */
@Data
@Schema(description = "房间重新开启请求")
public class ReopenRoomDto {

    @NotNull(message = "过期时长不能为空")
    @Min(value = 1, message = "过期时长最少为1小时")
    @Max(value = 168, message = "过期时长最多为168小时（7天）")
    @Schema(description = "重新开启后的过期时长（小时）", example = "24")
    private Integer expireHours;
}
