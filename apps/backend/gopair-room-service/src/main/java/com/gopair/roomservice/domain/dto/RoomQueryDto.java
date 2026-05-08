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
     * 是否包含历史已关闭房间和已过期房间
     * null/false：仅查询活跃房间（status=0）
     * true：查询活跃+已关闭+已过期（status IN (0,1,2)），不包含已归档
     */
    @Schema(description = "是否包含历史已关闭房间", example = "true")
    private Boolean includeHistory;

    /**
     * 房间状态筛选（0-活跃 1-已关闭 2-已过期 3-已归档）。
     * 优先级高于 includeHistory。注意：已归档房间不可通过此字段查出。
     */
    @Schema(description = "房间状态筛选（0-活跃 1-已关闭 2-已过期）", example = "1")
    private Integer status;
}
