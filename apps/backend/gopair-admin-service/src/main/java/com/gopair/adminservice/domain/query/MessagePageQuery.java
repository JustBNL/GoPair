package com.gopair.adminservice.domain.query;

/**
 * 消息分页查询条件。
 */
public record MessagePageQuery(
    Integer pageNum,
    Integer pageSize,
    Long roomId,
    Long senderId,
    Long ownerId,
    Integer messageType,
    Boolean isRecalled,
    String keyword,
    String startTime,
    String endTime
) {}
