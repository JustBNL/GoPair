package com.gopair.adminservice.domain.query;

/**
 * 通话分页查询条件。
 */
public record VoiceCallPageQuery(
    Integer pageNum,
    Integer pageSize,
    Long roomId,
    Long initiatorId,
    Integer callType,
    Integer status,
    String keyword,
    String startTime,
    String endTime
) {}
