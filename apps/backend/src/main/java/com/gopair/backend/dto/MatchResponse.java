package com.gopair.backend.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

/**
 * 匹配响应DTO，包含匹配尝试的结果
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MatchResponse {
    private boolean success;
    private String message;
    private String matchedUserId;
} 