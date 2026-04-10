package com.gopair.roomservice.stress;

import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 压力测试数据清理工具，提供对真实 Bean 的访问。
 *
 * 在 BaseIntegrationTest 中，Mapper 和 StringRedisTemplate 都被 MockBean 覆盖了，
 * 无法用于压力测试。此组件通过 @Autowired 注入真实 Bean，
 * 供压力测试进行数据初始化和清理。
 *
 * @author gopair
 */
@Component
public class RoomCacheCleaner {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ConnectionFactory connectionFactory;

    /**
     * 公开访问器（供静态上下文使用）
     */
    public StringRedisTemplate getRedisTemplate() { return redisTemplate; }
    public RoomMapper getRoomMapper() { return roomMapper; }
    public RoomMemberMapper getRoomMemberMapper() { return roomMemberMapper; }
    public RabbitTemplate getRabbitTemplate() { return rabbitTemplate; }
    public ConnectionFactory getConnectionFactory() { return connectionFactory; }

    /**
     * 清空所有测试房间相关的 Redis key 和数据库表。
     */
    public void cleanAll() {
        // 清空测试房间 Redis 数据
        Long[] testRoomIds = {998001L, 998002L, 998003L};
        for (Long roomId : testRoomIds) {
            redisTemplate.delete(RoomConst.metaKey(roomId));
            redisTemplate.delete(RoomConst.membersKey(roomId));
            redisTemplate.delete(RoomConst.pendingKey(roomId));
            redisTemplate.delete(RoomConst.metaInitLockKey(roomId));
        }

        // 清空数据库表（按依赖顺序：先子表再主表）
        try {
            roomMemberMapper.delete(null);
            roomMapper.delete(null);
        } catch (Exception e) {
            // H2 下某些 delete(null) 可能报错，忽略即可
        }
    }
}
