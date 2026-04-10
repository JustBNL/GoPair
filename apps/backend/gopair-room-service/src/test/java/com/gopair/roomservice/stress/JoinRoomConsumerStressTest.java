package com.gopair.roomservice.stress;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.event.JoinRoomRequestedEvent;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.messaging.JoinRoomConsumer;
import com.gopair.roomservice.messaging.JoinRoomProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MQ 消费层压力测试，验证 JoinRoomConsumer 在高吞吐下的正确性和性能。
 *
 * * [测试链路]
 * JoinRoomProducer.sendRequested → RabbitMQ Queue → JoinRoomConsumer.handle → DB + Redis
 *
 * * [测试场景]
 * - 幂等性：同一消息重复消费多次，DB/Redis 最终状态正确
 * - 吞吐能力：批量发送消息，测量 Consumer 的消费速度
 * - reserved 计数：消费后 reserved 正确扣减，不残留
 *
 * @author gopair
 */
@SpringBootTest(
    properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.concurrency=2",
        "spring.rabbitmq.listener.simple.max-concurrency=10",
    }
)
@ActiveProfiles("stress")
@DisplayName("MQ 消费层压力测试：JoinRoomConsumer 吞吐与幂等性")
public class JoinRoomConsumerStressTest {

    @SpyBean
    private JoinRoomConsumer joinRoomConsumer;

    @SpyBean
    private JoinRoomProducer joinRoomProducer;

    @SpyBean
    private RabbitTemplate rabbitTemplate;

    @SpyBean
    private ConnectionFactory connectionFactory;

    @Autowired
    private MessageConverter messageConverter;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    private static final Long TEST_ROOM_ID = 998003L;
    private static final Long OWNER_ID = 30000L;

    @BeforeEach
    void setUp() {
        cleanAll();
        createTestRoom();

        try {
            rabbitTemplate.execute(channel -> {
                channel.queuePurge("room.join.queue");
                return null;
            });
        } catch (Exception ignored) {}

        org.mockito.Mockito.reset(joinRoomConsumer, joinRoomProducer, rabbitTemplate);
    }

    private void createTestRoom() {
        Room room = new Room();
        room.setRoomId(TEST_ROOM_ID);
        room.setRoomCode(String.format("%08d", TEST_ROOM_ID));
        room.setRoomName("Consumer压测房间");
        room.setMaxMembers(200);
        room.setCurrentMembers(1);
        room.setOwnerId(OWNER_ID);
        room.setStatus(RoomConst.STATUS_ACTIVE);
        room.setPasswordMode(RoomConst.PASSWORD_MODE_NONE);
        room.setPasswordVisible(1);
        room.setExpireTime(LocalDateTime.now().plusHours(1));
        roomMapper.insert(room);

        RoomMember ownerMember = new RoomMember();
        ownerMember.setRoomId(TEST_ROOM_ID);
        ownerMember.setUserId(OWNER_ID);
        ownerMember.setRole(RoomConst.ROLE_OWNER);
        ownerMember.setStatus(RoomConst.MEMBER_STATUS_ONLINE);
        ownerMember.setJoinTime(LocalDateTime.now());
        ownerMember.setLastActiveTime(LocalDateTime.now());
        roomMemberMapper.insert(ownerMember);

        String metaKey = RoomConst.metaKey(TEST_ROOM_ID);
        long expireAtMs = LocalDateTime.now().plusHours(1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_MAX, "200");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_CONFIRMED, "1");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, "0");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_STATUS, "0");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_EXPIRE_AT, String.valueOf(expireAtMs));
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_PASSWORD_MODE, "0");
        redisTemplate.opsForSet().add(RoomConst.membersKey(TEST_ROOM_ID), String.valueOf(OWNER_ID));
    }

    private void cleanAll() {
        Long[] testRoomIds = {998001L, 998002L, 998003L};
        for (Long roomId : testRoomIds) {
            redisTemplate.delete(RoomConst.metaKey(roomId));
            redisTemplate.delete(RoomConst.membersKey(roomId));
            redisTemplate.delete(RoomConst.pendingKey(roomId));
            redisTemplate.delete(RoomConst.metaInitLockKey(roomId));
        }
        try {
            roomMemberMapper.delete(null);
            roomMapper.delete(null);
        } catch (Exception ignored) {}
    }

    // ==================== 幂等性测试 ====================

    @Nested
    @DisplayName("场景一：MQ 重试幂等性")
    class IdempotencyTests {

        @Test
        @DisplayName("同一消息重复消费 3 次，reserved 只扣减 1 次，room_member 只有 1 条")
        void testIdempotency_MultipleConsume() throws Exception {
            long userId = OWNER_ID + 1;
            String token = UUID.randomUUID().toString().replace("-", "");

            String metaKey = RoomConst.metaKey(TEST_ROOM_ID);
            String pendingKey = RoomConst.pendingKey(TEST_ROOM_ID);
            redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, "1");
            redisTemplate.opsForHash().put(pendingKey, String.valueOf(userId), token);
            redisTemplate.opsForValue().set(RoomConst.joinTokenKey(token), "PROCESSING");

            JoinRoomRequestedEvent event = new JoinRoomRequestedEvent(
                TEST_ROOM_ID, userId, token, System.currentTimeMillis());

            // 消费 3 次（模拟 MQ 重试）
            joinRoomConsumer.handle(event);
            joinRoomConsumer.handle(event);
            joinRoomConsumer.handle(event);

            // reserved 应为 0（只扣减 1 次）
            String reserved = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED);
            assertEquals("0", reserved, "reserved 应为 0（只扣减 1 次）");

            // room_member 只有 1 条
            RoomMember query = roomMemberMapper.selectOne(
                new LambdaQueryWrapper<RoomMember>()
                    .eq(RoomMember::getRoomId, TEST_ROOM_ID)
                    .eq(RoomMember::getUserId, userId));
            assertNotNull(query, "room_member 应有 1 条记录");
            assertEquals(userId, query.getUserId());

            // token 结果为 JOINED
            String tokenResult = redisTemplate.opsForValue().get(RoomConst.joinTokenKey(token));
            assertNotNull(tokenResult);
            assertTrue(tokenResult.contains("JOINED"), "token 应为 JOINED，实际=" + tokenResult);

            System.out.println("[MQ幂等] 同一消息消费3次，reserved=0, room_member=1条, token=JOINED ✓");
        }

        @Test
        @DisplayName("同一用户两次不同 token：第1个 JOINED，第2个 FAILED（用户已在房）")
        void testIdempotency_TwoTokensSameUser() {
            long userId = OWNER_ID + 2;

            // 第1个 token 入房
            String token1 = UUID.randomUUID().toString().replace("-", "");
            JoinRoomRequestedEvent event1 = new JoinRoomRequestedEvent(
                TEST_ROOM_ID, userId, token1, System.currentTimeMillis());

            String metaKey = RoomConst.metaKey(TEST_ROOM_ID);
            String pendingKey = RoomConst.pendingKey(TEST_ROOM_ID);
            redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, "1");
            redisTemplate.opsForHash().put(pendingKey, String.valueOf(userId), token1);

            joinRoomConsumer.handle(event1);

            String result1 = redisTemplate.opsForValue().get(RoomConst.joinTokenKey(token1));
            assertTrue(result1.contains("JOINED"), "第1个 token 应 JOINED");

            // 第2个 token（用户已在 members，不在 pending）→ FAILED
            String token2 = UUID.randomUUID().toString().replace("-", "");
            JoinRoomRequestedEvent event2 = new JoinRoomRequestedEvent(
                TEST_ROOM_ID, userId, token2, System.currentTimeMillis());

            redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, "1");
            redisTemplate.opsForHash().put(pendingKey, String.valueOf(userId), token2);

            joinRoomConsumer.handle(event2);

            String result2 = redisTemplate.opsForValue().get(RoomConst.joinTokenKey(token2));
            assertTrue(result2.contains("FAILED"), "第2个 token 应 FAILED（用户已入房）");

            System.out.println("[MQ幂等] token1=JOINED, token2=FAILED ✓");
        }
    }

    // ==================== 吞吐测试 ====================

    @Nested
    @DisplayName("场景二：消费吞吐能力")
    class ThroughputTests {

        @Test
        @DisplayName("批量发送 100 条消息，测量 Consumer 吞吐")
        void testConsumerThroughput_100Messages() throws Exception {
            int messageCount = 100;
            List<String> tokens = new ArrayList<>();

            String metaKey = RoomConst.metaKey(TEST_ROOM_ID);
            String pendingKey = RoomConst.pendingKey(TEST_ROOM_ID);

            for (int i = 0; i < messageCount; i++) {
                long userId = OWNER_ID + 100 + i;
                String token = UUID.randomUUID().toString().replace("-", "");
                tokens.add(token);
                redisTemplate.opsForHash().put(pendingKey, String.valueOf(userId), token);
            }
            redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, String.valueOf(messageCount));

            long sendStart = System.currentTimeMillis();
            for (int i = 0; i < messageCount; i++) {
                long userId = OWNER_ID + 100 + i;
                JoinRoomRequestedEvent event = new JoinRoomRequestedEvent(
                    TEST_ROOM_ID, userId, tokens.get(i), System.currentTimeMillis());
                joinRoomProducer.sendRequested(event);
            }
            long sendElapsedMs = System.currentTimeMillis() - sendStart;

            long consumeStart = System.currentTimeMillis();
            boolean allProcessed = awaitAllTokensJoined(tokens, 15);
            long consumeElapsedMs = System.currentTimeMillis() - consumeStart;

            assertTrue(allProcessed, "所有消息应在 15 秒内消费完成");

            double throughput = messageCount * 1000.0 / consumeElapsedMs;
            System.out.printf("[MQ吞吐] 发送耗时=%dms, 消费耗时=%dms, 吞吐量=%.1f条/秒%n",
                    sendElapsedMs, consumeElapsedMs, throughput);

            String reserved = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED);
            assertEquals("0", reserved, "reserved 应为 0（全部释放），实际=" + reserved);

            assertTrue(throughput > 10, "Consumer 吞吐应 > 10条/秒，实际=" + throughput);
        }

        @Test
        @DisplayName("200 线程并发入房，系统在高并发下不崩溃、reserved 不为负")
        void testHighConcurrency_NoCrash() throws Exception {
            int threadCount = 200;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long userId = OWNER_ID + idx + 1000;
                        String token = UUID.randomUUID().toString().replace("-", "");
                        JoinRoomRequestedEvent event = new JoinRoomRequestedEvent(
                            TEST_ROOM_ID, userId, token, System.currentTimeMillis());
                        try {
                            joinRoomProducer.sendRequested(event);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        failCount.incrementAndGet();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS), "200线程并发应在30秒内完成");
            long elapsedMs = System.currentTimeMillis() - startTime;
            executor.shutdown();

            System.out.printf("[MQ高压] 200线程发送，耗时=%dms, 成功=%d, 失败=%d%n",
                    elapsedMs, successCount.get(), failCount.get());

            String metaKey = RoomConst.metaKey(TEST_ROOM_ID);
            String reserved = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED);
            int reservedValue = reserved != null ? Integer.parseInt(reserved) : 0;
            assertTrue(reservedValue >= 0, "reserved 不应为负数，实际=" + reservedValue);

            System.out.println("[MQ高压] reserved=" + reservedValue + " (不为负) ✓");
        }
    }

    // ==================== reserved 计数准确性 ====================

    @Nested
    @DisplayName("场景三：reserved 计数准确性")
    class ReservedCountTests {

        @Test
        @DisplayName("消费后 reserved 精确扣减，不残留、不变负")
        void testReserved_ConsumeDecrement() {
            String metaKey = RoomConst.metaKey(TEST_ROOM_ID);
            String pendingKey = RoomConst.pendingKey(TEST_ROOM_ID);

            for (int i = 0; i < 5; i++) {
                long userId = OWNER_ID + 5000 + i;
                String token = "reserved-test-token-" + i;
                redisTemplate.opsForHash().put(pendingKey, String.valueOf(userId), token);
            }
            redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, "5");

            for (int i = 0; i < 5; i++) {
                long userId = OWNER_ID + 5000 + i;
                String token = "reserved-test-token-" + i;
                JoinRoomRequestedEvent event = new JoinRoomRequestedEvent(
                    TEST_ROOM_ID, userId, token, System.currentTimeMillis());
                joinRoomConsumer.handle(event);

                String reserved = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED);
                int expectedReserved = 5 - (i + 1);
                assertEquals(String.valueOf(expectedReserved), reserved,
                        "第" + (i + 1) + "次消费后 reserved 应为 " + expectedReserved + "，实际=" + reserved);
            }

            String finalReserved = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED);
            assertEquals("0", finalReserved, "所有消费完成后 reserved 应为 0");

            System.out.println("[reserved计数] 5次消费 reserved: 5→4→3→2→1→0 ✓");
        }

        @Test
        @DisplayName("正常消费后 reserved=0，pending 已删除")
        void testReserved_NormalConsumeRelease() {
            long userId = OWNER_ID + 6000;
            String token = "exception-test-token";

            String metaKey = RoomConst.metaKey(TEST_ROOM_ID);
            String pendingKey = RoomConst.pendingKey(TEST_ROOM_ID);
            redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, "1");
            redisTemplate.opsForHash().put(pendingKey, String.valueOf(userId), token);

            JoinRoomRequestedEvent event = new JoinRoomRequestedEvent(
                TEST_ROOM_ID, userId, token, System.currentTimeMillis());

            joinRoomConsumer.handle(event);

            String reserved = (String) redisTemplate.opsForHash().get(metaKey, RoomConst.FIELD_RESERVED);
            assertEquals("0", reserved, "正常消费后 reserved 应为 0");

            Boolean pendingExists = redisTemplate.opsForHash().hasKey(pendingKey, String.valueOf(userId));
            assertEquals(Boolean.FALSE, pendingExists, "pending 应已被删除");

            System.out.println("[reserved计数] 正常消费 reserved=0, pending已删除 ✓");
        }
    }

    // ==================== 辅助方法 ====================

    private boolean awaitAllTokensJoined(List<String> tokens, int maxSeconds) {
        for (int sec = 0; sec < maxSeconds; sec++) {
            int joined = 0;
            for (String token : tokens) {
                String result = redisTemplate.opsForValue().get(RoomConst.joinTokenKey(token));
                if (result != null && result.contains("JOINED")) {
                    joined++;
                }
            }
            if (joined == tokens.size()) {
                return true;
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        return false;
    }
}
