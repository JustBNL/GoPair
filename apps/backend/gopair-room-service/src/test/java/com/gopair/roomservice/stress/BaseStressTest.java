package com.gopair.roomservice.stress;

import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.config.RoomConfig;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

/**
 * 压力测试基础类，提供真实的 Redis 连接和 H2 数据库。
 *
 * 与普通集成测试（BaseIntegrationTest）的区别：
 * - Redis 使用真实连接（而非 MockBean），用于验证 Lua 脚本原子性
 * - H2 数据库使用真实内存库（而非 @MockBean Mapper）
 * - MQ 消费者自动启动（用于端到端测试）
 *
 * @author gopair
 */
@SpringBootTest(
    properties = {
        // 禁用 Nacos（测试环境不需要）
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        // 关闭 RabbitMQ 消费者自动启动（消费层测试需要手动触发）
        "spring.rabbitmq.listener.simple.auto-startup=false",
        // 关闭 JPA 自动 ddl（使用 schema.sql 初始化）
        "spring.jpa.hibernate.ddl-auto=none",
    }
)
@ActiveProfiles("stress")
@Import(RoomConfig.class)
public abstract class BaseStressTest {

    @Autowired
    protected StringRedisTemplate redisTemplate;

    @Autowired
    protected RoomMapper roomMapper;

    @Autowired
    protected RoomMemberMapper roomMemberMapper;

    /**
     * 每个测试方法执行前：
     * 1. 清空 Redis 中与测试房间相关的所有 key（防止数据污染）
     * 2. 清空测试数据库表
     */
    @BeforeEach
    protected void cleanStressTestData() {
        // 清空测试房间的 Redis 数据（meta、members、pending、lock）
        Long[] testRoomIds = {998001L, 998002L, 998003L};
        for (Long roomId : testRoomIds) {
            redisTemplate.delete(RoomConst.metaKey(roomId));
            redisTemplate.delete(RoomConst.membersKey(roomId));
            redisTemplate.delete(RoomConst.pendingKey(roomId));
            redisTemplate.delete(RoomConst.metaInitLockKey(roomId));
        }

        // 清空测试表（按依赖顺序：先删子表再删主表）
        roomMemberMapper.delete(null);
        roomMapper.delete(null);
    }

    /**
     * 创建压力测试用房间，插入 DB 并初始化 Redis 缓存。
     * 房间容量较小（maxMembers=5），便于验证防超卖。
     *
     * @param roomId   房间 ID
     * @param ownerId  房主 ID（自动加入房间）
     * @param maxMembers 最大成员数
     * @return 创建的房间实体
     */
    protected Room createTestRoom(Long roomId, Long ownerId, int maxMembers) {
        Room room = new Room();
        room.setRoomId(roomId);
        room.setRoomCode(String.format("%08d", roomId));
        room.setRoomName("压力测试房间-" + roomId);
        room.setMaxMembers(maxMembers);
        room.setCurrentMembers(1); // 房主自动加入
        room.setOwnerId(ownerId);
        room.setStatus(RoomConst.STATUS_ACTIVE);
        room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
        room.setPasswordVisible(1);
        room.setExpireTime(LocalDateTime.now().plusHours(1));

        roomMapper.insert(room);

        // 房主写入 room_member
        com.gopair.roomservice.domain.po.RoomMember ownerMember =
            new com.gopair.roomservice.domain.po.RoomMember();
        ownerMember.setRoomId(roomId);
        ownerMember.setUserId(ownerId);
        ownerMember.setRole(RoomConst.ROLE_OWNER);
        ownerMember.setStatus(RoomConst.MEMBER_STATUS_ONLINE);
        ownerMember.setJoinTime(LocalDateTime.now());
        ownerMember.setLastActiveTime(LocalDateTime.now());
        roomMemberMapper.insert(ownerMember);

        // 初始化 Redis 缓存（房主已加入，confirmed=1）
        redisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_MAX, String.valueOf(maxMembers));
        redisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_CONFIRMED, "1");
        redisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_RESERVED, "0");
        redisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_STATUS, String.valueOf(RoomConst.STATUS_ACTIVE));
        long expireAtMs = room.getExpireTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        redisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_EXPIRE_AT, String.valueOf(expireAtMs));
        redisTemplate.opsForHash().put(RoomConst.metaKey(roomId), RoomConst.FIELD_PASSWORD_MODE, String.valueOf(RoomConst.PASSWORD_MODE_NONE));
        redisTemplate.opsForSet().add(RoomConst.membersKey(roomId), String.valueOf(ownerId));

        return room;
    }

    /**
     * 从 Redis 读取房间的元数据快照。
     */
    protected RoomMetaSnapshot readMeta(Long roomId) {
        String metaKey = RoomConst.metaKey(roomId);
        String confirmed = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_CONFIRMED);
        String reserved = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED);
        String max = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_MAX);
        return new RoomMetaSnapshot(
            max != null ? Integer.parseInt(max) : 0,
            confirmed != null ? Integer.parseInt(confirmed) : 0,
            reserved != null ? Integer.parseInt(reserved) : 0
        );
    }

    /**
     * 房间 Redis 元数据快照（用于验证）
     */
    public record RoomMetaSnapshot(int max, int confirmed, int reserved) {
        int totalOccupied() { return confirmed + reserved; }
        boolean isFull() { return totalOccupied() >= max; }
        // 显式公开字段访问器（record 自动生成，但显式声明更清晰）
        public int max() { return max; }
        public int confirmed() { return confirmed; }
        public int reserved() { return reserved; }
    }
}
