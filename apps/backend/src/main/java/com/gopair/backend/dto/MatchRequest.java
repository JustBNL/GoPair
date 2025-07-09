package com.gopair.backend.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * 匹配请求DTO，包含用于匹配用户的密钥
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MatchRequest {
    private String key;
} 