package com.gopair.roomservice.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户离线事件，当用户所有 WebSocket 连接断开时触发。
 * 用于将 room_member.status 从在线（0）更新为离线（1）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserOfflineEvent implements Serializable {
    private Long userId;
}
