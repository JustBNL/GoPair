package com.gopair.adminservice.domain.query;

/**
 * 文件分页查询条件。
 */
public record FilePageQuery(
    Integer pageNum,
    Integer pageSize,
    Long roomId,
    Long uploaderId,
    String fileType,
    String keyword,
    String startTime,
    String endTime
) {}
