package com.gopair.roomservice.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomResultEvent implements Serializable {
    public enum Status { JOINED, FAILED }
    private String joinToken;
    private Long roomId;
    private Long userId;
    private Status status;
    private String reason;
} 