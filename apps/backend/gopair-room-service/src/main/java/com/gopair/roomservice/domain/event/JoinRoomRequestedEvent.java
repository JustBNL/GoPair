package com.gopair.roomservice.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinRoomRequestedEvent implements Serializable {
    private Long roomId;
    private Long userId;
    private String joinToken;
    private Long requestAt;
} 