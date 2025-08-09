package com.gopair.common.entity;

import lombok.Data;
import java.io.Serializable;

/**
 * 查询基类
 * 
 * 所有查询DTO的基类，包含分页查询的通用参数
 * 
 * @author gopair
 */
@Data
public class BaseQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    private String orderBy;

    /**
     * 排序方向（asc/desc）
     */
    private String orderDirection = "desc";
} 