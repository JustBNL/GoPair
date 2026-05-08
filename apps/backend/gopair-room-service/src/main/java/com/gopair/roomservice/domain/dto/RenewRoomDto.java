package com.gopair.roomservice.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 房间续期请求
 *
 * @author gopair
 */
@Data
@Schema(description = "房间续期请求")
public class RenewRoomDto {

    @NotNull(message = "续期时长不能为空")
    @Min(value = 1, message = "续期时长最少为1小时")
    @Max(value = 168, message = "续期时长最多为168小时（7天）")
    @Schema(description = "续期时长（小时）", example = "24")
    private Integer extendHours;
}
