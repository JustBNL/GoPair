package com.gopair.roomservice.domain.dto;

import com.gopair.common.entity.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户房间列表查询参数
 *
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "用户房间列表查询参数")
public class RoomQueryDto extends BaseQuery {

    /**
     * 是否包含历史已关闭房间
     * null/false：默认仅查询活跃房间
     * true：查询全部状态房间（除非显式指定 status）
     */
    @Schema(description = "是否包含历史已关闭房间", example = "true")
    private Boolean includeHistory;

    /**
     * 房间状态筛选（0-活跃 1-已关闭）
     * 优先级高于 includeHistory
     */
    @Schema(description = "房间状态筛选（0-活跃 1-已关闭）", example = "1")
    private Integer status;
}
