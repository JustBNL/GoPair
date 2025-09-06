package com.gopair.messageservice.domain.dto;

import com.gopair.common.entity.BaseQuery;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotNull;

/**
 * 消息查询DTO
 * 
 * @author gopair
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "消息查询请求")
public class MessageQueryDto extends BaseQuery {

    /**
     * 房间ID
     */
    @NotNull(message = "房间ID不能为空")
    @Schema(description = "房间ID", example = "1")
    private Long roomId;

    /**
     * 消息类型筛选（可选）
     */
    @Schema(description = "消息类型", example = "1")
    private Integer messageType;

    /**
     * 发送者ID筛选（可选）
     */
    @Schema(description = "发送者ID", example = "1")
    private Long senderId;

    /**
     * 关键词搜索（可选）
     */
    @Schema(description = "搜索关键词", example = "hello")
    private String keyword;
} 