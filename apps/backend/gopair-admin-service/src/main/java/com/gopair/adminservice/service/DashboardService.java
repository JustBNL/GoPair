package com.gopair.adminservice.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.adminservice.domain.po.Message;
import com.gopair.adminservice.domain.po.Room;
import com.gopair.adminservice.domain.po.User;
import com.gopair.adminservice.domain.po.VoiceCall;
import com.gopair.adminservice.mapper.MessageMapper;
import com.gopair.adminservice.mapper.RoomMapper;
import com.gopair.adminservice.mapper.UserMapper;
import com.gopair.adminservice.mapper.VoiceCallMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private final MessageMapper messageMapper;

    public record DashboardStats(
            Long totalUsers,
            Long todayNewUsers,
            Long activeRooms,
            Long todayNewRooms,
            Long todayMessages,
            Long todayVoiceCallDuration
    ) {}

    public record DailyStats(
            String date,
            Long newUsers,
            Long newRooms,
            Long messages,
            Long voiceCallDuration
    ) {}

    public record RoomStatusDistribution(
            Long active,
            Long closed,
            Long expired
    ) {}

    public record RecentRoom(
            Long roomId,
            String roomName,
            Integer status,
            Integer currentMembers,
            Integer maxMembers,
            String createTime
    ) {}

    public record DashboardTrends(
            List<DailyStats> daily,
            RoomStatusDistribution roomStatusDistribution,
            Long totalUsers,
            Long totalRooms,
            Long totalMessages
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
        stats.put("todayMessages", messageMapper.selectCount(
                new LambdaQueryWrapper<Message>().ge(Message::getCreateTime, todayStart)
        ));
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

    public DashboardTrends getTrends() {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM-dd");
        List<DailyStats> dailyStats = new ArrayList<>();

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

            LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
            userWrapper.ge(User::getCreateTime, dayStart).le(User::getCreateTime, dayEnd);
            long newUsers = userMapper.selectCount(userWrapper);

            LambdaQueryWrapper<Room> roomWrapper = new LambdaQueryWrapper<>();
            roomWrapper.ge(Room::getCreateTime, dayStart).le(Room::getCreateTime, dayEnd);
            long newRooms = roomMapper.selectCount(roomWrapper);

            LambdaQueryWrapper<Message> msgWrapper = new LambdaQueryWrapper<>();
            msgWrapper.ge(Message::getCreateTime, dayStart).le(Message::getCreateTime, dayEnd);
            long messages = messageMapper.selectCount(msgWrapper);

            LambdaQueryWrapper<VoiceCall> voiceWrapper = new LambdaQueryWrapper<>();
            voiceWrapper.ge(VoiceCall::getStartTime, dayStart).le(VoiceCall::getStartTime, dayEnd);
            voiceWrapper.eq(VoiceCall::getStatus, 2);
            long voiceDuration = voiceCallMapper.selectList(voiceWrapper).stream()
                    .mapToLong(v -> v.getDuration() != null ? v.getDuration() : 0L)
                    .sum();

            dailyStats.add(new DailyStats(date.format(dateFormatter), newUsers, newRooms, messages, voiceDuration));
        }

        long activeRooms = roomMapper.selectCount(new LambdaQueryWrapper<Room>().eq(Room::getStatus, 0));
        long closedRooms = roomMapper.selectCount(new LambdaQueryWrapper<Room>().eq(Room::getStatus, 1));
        long expiredRooms = roomMapper.selectCount(new LambdaQueryWrapper<Room>().eq(Room::getStatus, 2));
        RoomStatusDistribution distribution = new RoomStatusDistribution(activeRooms, closedRooms, expiredRooms);

        long totalMessages = messageMapper.selectCount(null);

        log.debug("[Dashboard] 获取7日趋势数据，共 {} 条", dailyStats.size());
        return new DashboardTrends(dailyStats, distribution, userMapper.selectCount(null), roomMapper.selectCount(null), totalMessages);
    }

    public List<RecentRoom> getRecentRooms(int limit) {
        LambdaQueryWrapper<Room> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Room::getCreateTime).last("LIMIT " + limit);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return roomMapper.selectList(wrapper).stream()
                .map(room -> new RecentRoom(
                        room.getRoomId(),
                        room.getRoomName(),
                        room.getStatus(),
                        room.getCurrentMembers(),
                        room.getMaxMembers(),
                        room.getCreateTime().format(fmt)
                ))
                .toList();
    }
}
