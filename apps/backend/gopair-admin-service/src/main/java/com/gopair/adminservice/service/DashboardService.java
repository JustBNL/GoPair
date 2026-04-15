package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.domain.po.User;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.mapper.RoomMapper;
import com.gopair.adminservice.mapper.UserMapper;
import com.gopair.adminservice.mapper.VoiceCallMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 仪表盘统计数据服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final UserMapper userMapper;
    private final RoomMapper roomMapper;
    private final VoiceCallMapper voiceCallMapper;

    public record DashboardStats(
            Long totalUsers,
            Long todayNewUsers,
            Long activeRooms,
            Long todayNewRooms,
            Long todayMessages,
            Long todayVoiceCallDuration
    ) {}

    public DashboardStats getStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(LocalTime.MAX);

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.ge(User::getCreateTime, todayStart);
        long todayNewUsers = userMapper.selectCount(userWrapper);

        LambdaQueryWrapper<Room> roomWrapper = new LambdaQueryWrapper<>();
        roomWrapper.ge(Room::getCreateTime, todayStart);
        long todayNewRooms = roomMapper.selectCount(roomWrapper);

        LambdaQueryWrapper<VoiceCall> voiceWrapper = new LambdaQueryWrapper<>();
        voiceWrapper.ge(VoiceCall::getStartTime, todayStart);
        voiceWrapper.le(VoiceCall::getStartTime, todayEnd);
        voiceWrapper.eq(VoiceCall::getStatus, 2);
        Long voiceCallDuration = voiceCallMapper.selectList(voiceWrapper).stream()
                .mapToLong(v -> v.getDuration() != null ? v.getDuration() : 0L)
                .sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("todayNewUsers", todayNewUsers);
        stats.put("activeRooms", roomMapper.selectCount(
                new LambdaQueryWrapper<Room>().eq(Room::getStatus, 0)
        ));
        stats.put("todayNewRooms", todayNewRooms);
        stats.put("todayMessages", 0L);
        stats.put("todayVoiceCallDuration", voiceCallDuration);

        log.debug("[Dashboard] 获取统计数据: {}", stats);

        return new DashboardStats(
                (Long) stats.get("totalUsers"),
                (Long) stats.get("todayNewUsers"),
                (Long) stats.get("activeRooms"),
                (Long) stats.get("todayNewRooms"),
                (Long) stats.get("todayMessages"),
                (Long) stats.get("todayVoiceCallDuration")
        );
    }
}
