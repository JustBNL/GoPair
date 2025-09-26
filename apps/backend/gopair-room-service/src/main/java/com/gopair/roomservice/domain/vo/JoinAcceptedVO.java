package com.gopair.roomservice.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinAcceptedVO {
    private String joinToken;
    private String message;
} 