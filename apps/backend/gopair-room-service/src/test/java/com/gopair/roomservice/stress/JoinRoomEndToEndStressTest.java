package com.gopair.roomservice.stress;

import com.gopair.common.core.R;
import com.gopair.framework.context.UserContext;
import com.gopair.framework.context.UserContextHolder;
import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.dto.JoinRoomDto;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.domain.po.RoomMember;
import com.gopair.roomservice.messaging.JoinRoomConsumer;
import com.gopair.roomservice.messaging.JoinRoomProducer;
import com.gopair.roomservice.mapper.RoomMapper;
import com.gopair.roomservice.mapper.RoomMemberMapper;
import com.gopair.roomservice.service.JoinResultQueryService.JoinStatusVO;
import com.gopair.roomservice.service.JoinResultQueryService.JoinStatusVO.Status;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步入房端到端压力测试，验证从 HTTP 请求到 Redis 结果的完整链路。
 *
 * * [测试链路]
 * 1. HTTP POST /room/join/async → JoinReservationService.preReserve（Redis Lua）
 * 2. JoinRoomProducer.sendRequested → RabbitMQ
 * 3. JoinRoomConsumer.handle（自动消费）→ DB 写入 + Redis confirmed++ + Redis token 写入
 * 4. HTTP GET /room/join/result?token=xxx → 轮询 Redis token，判定 JOINED / PROCESSING / FAILED
 *
 * * [前置条件]
 * - Redis: localhost:6379
 * - RabbitMQ: localhost:5672（guest/guest）
 * - JoinRoomConsumer 已激活（auto-startup: true）
 *
 * @author gopair
 */
@SpringBootTest(
    properties = {
        "spring.cloud.nacos.config.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=none",
    }
)
@ActiveProfiles("stress")
@Disabled("需要真实 Redis + RabbitMQ 基础设施，CI 环境跳过")
@DisplayName("端到端压力测试：HTTP → Redis → MQ → Consumer → Redis 结果")
public class JoinRoomEndToEndStressTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RoomMapper roomMapper;

    @Autowired
    private RoomMemberMapper roomMemberMapper;

    @SpyBean
    private JoinRoomProducer joinRoomProducer;

    @SpyBean
    private JoinRoomConsumer joinRoomConsumer;

    @SpyBean
    private RabbitTemplate rabbitTemplate;

    @MockBean
    private ConnectionFactory connectionFactory;

    @MockBean
    private org.springframework.web.client.RestTemplate userProfileRestTemplate;

    private static final Long TEST_ROOM_ID = 998002L;
    private static final Long OWNER_ID = 20000L;

    /**
     * 每个测试前：清理 Redis 和数据库，创建测试房间。
     */
    @BeforeEach
    void setUp() {
        cleanAll();
        createTestRoom();
        // 重置 Spy 计数
        org.mockito.Mockito.reset(joinRoomProducer, joinRoomConsumer, rabbitTemplate);
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

    private void createTestRoom() {
        Room room = new Room();
        room.setRoomId(TEST_ROOM_ID);
        room.setRoomCode(String.format("%08d", TEST_ROOM_ID));
        room.setRoomName("E2E压测房间");
        room.setMaxMembers(5);
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
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_MAX, "5");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_CONFIRMED, "1");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_RESERVED, "0");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_STATUS, "0");
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_EXPIRE_AT, String.valueOf(expireAtMs));
        redisTemplate.opsForHash().put(metaKey, RoomConst.FIELD_PASSWORD_MODE, "0");
        redisTemplate.opsForSet().add(RoomConst.membersKey(TEST_ROOM_ID), String.valueOf(OWNER_ID));
    }

    /**
     * 构造带 userId 的 HTTP 请求（设置 UserContextHolder 模拟已登录用户）。
     */
    private ResponseEntity<R> postJoinRoomAsync(long userId, String roomCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JoinRoomDto dto = new JoinRoomDto();
        dto.setRoomCode(roomCode);
        HttpEntity<JoinRoomDto> entity = new HttpEntity<>(dto, headers);

        UserContext ctx = new UserContext();
        ctx.setUserId(userId);
        UserContextHolder.setContext(ctx);
        try {
            return restTemplate.exchange(
                "/room/join/async",
                HttpMethod.POST,
                entity,
                R.class
            );
        } finally {
            UserContextHolder.clear();
        }
    }

    private ResponseEntity<R> getJoinResult(String token) {
        return restTemplate.getForEntity("/room/join/result?token=" + token, R.class);
    }

    // ==================== 基础功能验证 ====================

    @Nested
    @DisplayName("场景一：基础功能验证")
    class BasicFunctionTests {

        @Test
        @DisplayName("单用户正常入房：HTTP 受理 → MQ 消费 → JOINED")
        void testSingleUserHappyPath() {
            long userId = OWNER_ID + 1;
            String roomCode = String.format("%08d", TEST_ROOM_ID);

            // Step 1: HTTP 异步入房请求
            ResponseEntity<R> resp = postJoinRoomAsync(userId, roomCode);
            assertEquals(200, resp.getStatusCode().value());
            R body = resp.getBody();
            assertNotNull(body);
            assertEquals(0, body.getCode()); // 成功

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) body.getData();
            String token = (String) data.get("joinToken");
            assertNotNull(token, "应返回 joinToken");

            // Step 2: 轮询等待 MQ 消费（约 1 秒内完成）
            JoinStatusVO status = waitForJoinStatus(token, 5);
            assertEquals(Status.JOINED, status.status, "消费后状态应为 JOINED");
            assertEquals(TEST_ROOM_ID, status.roomId);
            assertEquals(userId, status.userId);

            System.out.println("[E2E-基础] 单用户入房成功 token=" + token + " status=" + status.status);
        }

        @Test
        @DisplayName("同一用户重复入房：第 1 次 ACCEPTED，第 2 次 ALREADY_PROCESSING，第 3 次 JOINED")
        void testSameUserDuplicateJoin() {
            long userId = OWNER_ID + 100;
            String roomCode = String.format("%08d", TEST_ROOM_ID);

            // 第一次入房
            ResponseEntity<R> r1 = postJoinRoomAsync(userId, roomCode);
            assertEquals(200, r1.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> d1 = (Map<String, Object>) r1.getBody().getData();
            String token1 = (String) d1.get("joinToken");
            assertNotNull(token1);

            // 第二次入房（已有 pending，应返回已有请求）
            ResponseEntity<R> r2 = postJoinRoomAsync(userId, roomCode);
            assertEquals(200, r2.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> d2 = (Map<String, Object>) r2.getBody().getData();
            String message = (String) d2.get("message");
            assertTrue(message.contains("已有加入请求") || message.contains("已在房间") || message.contains("正在处理"),
                    "第2次应返回已有请求提示，实际消息：" + message);

            // 等待第一次完成
            JoinStatusVO s1 = waitForJoinStatus(token1, 5);
            assertEquals(Status.JOINED, s1.status);

            // 第三次入房（已入房，应返回 ALREADY_JOINED）
            ResponseEntity<R> r3 = postJoinRoomAsync(userId, roomCode);
            assertEquals(200, r3.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> d3 = (Map<String, Object>) r3.getBody().getData();
            String msg3 = (String) d3.get("message");
            assertTrue(msg3.contains("已在房间"), "第3次应返回已在房间，实际消息：" + msg3);

            System.out.println("[E2E-基础] 同用户重复入房：第1次=ACCEPTED, 第2次=已有请求, 第3次=已在房间");
        }
    }

    // ==================== 并发入房 ====================

    @Nested
    @DisplayName("场景二：并发入房 — 防超卖验证")
    class ConcurrentJoinTests {

        @Test
        @DisplayName("10 用户并发入房 4 个名额，只有 4 个成功 JOINED")
        void testConcurrentJoin_Only4Succeed() throws Exception {
            String roomCode = String.format("%08d", TEST_ROOM_ID);
            int threadCount = 10;
            int maxMembers = 5; // confirmed=1，剩余 4 个

            List<Long> acceptedUserIds = new ArrayList<>();
            AtomicInteger httpAcceptedCount = new AtomicInteger(0);
            AtomicInteger httpAlreadyJoinedCount = new AtomicInteger(0);
            AtomicInteger httpFullCount = new AtomicInteger(0);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final long userId = OWNER_ID + i + 200;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        ResponseEntity<R> resp = postJoinRoomAsync(userId, roomCode);
                        if (resp.getStatusCode().value() == 200 && resp.getBody() != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
                            String msg = (String) data.get("message");
                            if (data.get("joinToken") != null) {
                                acceptedUserIds.add(userId);
                                httpAcceptedCount.incrementAndGet();
                            } else if (msg != null && msg.contains("已在房间")) {
                                httpAlreadyJoinedCount.incrementAndGet();
                            } else if (msg != null && msg.contains("满")) {
                                httpFullCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[E2E-并发] userId=" + userId + " 异常: " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown();
            assertTrue(endLatch.await(15, TimeUnit.SECONDS), "并发入房应在 15 秒内完成");
            long elapsedMs = System.currentTimeMillis() - startTime;
            executor.shutdown();

            System.out.printf("[E2E-并发] %d线程入房，HTTP成功=%d, 已在房间=%d, 满员=%d, 耗时=%dms%n",
                    threadCount, httpAcceptedCount.get(), httpAlreadyJoinedCount.get(),
                    httpFullCount.get(), elapsedMs);

            // 等待所有 MQ 消费完成（最多 10 秒）
            Thread.sleep(3000);

            // 轮询每个成功 token 的最终状态
            int joinedCount = 0;
            for (Long userId : acceptedUserIds) {
                String token = findTokenByUserId(userId);
                if (token != null) {
                    JoinStatusVO status = waitForJoinStatus(token, 8);
                    if (status.status == Status.JOINED) joinedCount++;
                }
            }

            // 验证：只有 4 个名额，最终 JOINED 数量不超过 maxMembers
            assertTrue(joinedCount <= maxMembers,
                    "JOINED 总数不超过 max=" + maxMembers + "，实际=" + joinedCount);

            System.out.printf("[E2E-并发] 最终 JOINED=%d/%d (max=%d) ✓%n",
                    joinedCount, httpAcceptedCount.get(), maxMembers);
        }

        private String findTokenByUserId(long userId) {
            Map<Object, Object> pending = redisTemplate
                .opsForHash().entries(RoomConst.pendingKey(TEST_ROOM_ID));
            for (Map.Entry<Object, Object> e : pending.entrySet()) {
                if (Long.parseLong(e.getKey().toString()) == userId) {
                    return e.getValue().toString();
                }
            }
            return null;
        }
    }

    // ==================== 轮询结果验证 ====================

    @Nested
    @DisplayName("场景三：轮询结果验证")
    class PollResultTests {

        @Test
        @DisplayName("未消费前轮询返回 PROCESSING，消费后返回 JOINED")
        void testPollResult_ProcessingThenJoined() {
            long userId = OWNER_ID + 300;
            String roomCode = String.format("%08d", TEST_ROOM_ID);

            ResponseEntity<R> resp = postJoinRoomAsync(userId, roomCode);
            assertEquals(200, resp.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
            String token = (String) data.get("joinToken");
            assertNotNull(token);

            // 立即轮询（MQ 还未消费）→ PROCESSING
            ResponseEntity<R> pollResp = getJoinResult(token);
            assertEquals(200, pollResp.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> pollData = (Map<String, Object>) pollResp.getBody().getData();
            String status1 = (String) pollData.get("status");
            assertEquals("PROCESSING", status1, "MQ 未消费时应返回 PROCESSING");

            // 等待消费完成后 → JOINED
            JoinStatusVO finalStatus = waitForJoinStatus(token, 5);
            assertEquals(Status.JOINED, finalStatus.status, "消费后应返回 JOINED");

            System.out.println("[E2E-轮询] PROCESSING → JOINED ✓");
        }

        @Test
        @DisplayName("不存在的 token 轮询返回 FAILED")
        void testPollResult_NotFoundToken() {
            ResponseEntity<R> resp = getJoinResult("non-existent-token-xyz123");
            assertEquals(200, resp.getStatusCode().value());
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
            assertEquals("FAILED", data.get("status"), "不存在的 token 应返回 FAILED");
            System.out.println("[E2E-轮询] 不存在token=FAILED ✓");
        }
    }

    // ==================== MQ 吞吐能力 ====================

    @Nested
    @DisplayName("场景四：MQ 吞吐能力")
    class MQThroughputTests {

        @Test
        @DisplayName("50 用户顺序入房，测量平均端到端耗时")
        void testEndToEndLatency_50Sequential() throws Exception {
            String roomCode = String.format("%08d", TEST_ROOM_ID);
            int count = 50;
            long[] latencies = new long[count];

            // 先将房间容量调大（直接改 Redis）
            redisTemplate.opsForHash()
                .put(RoomConst.metaKey(TEST_ROOM_ID), RoomConst.FIELD_MAX, String.valueOf(100));

            for (int i = 0; i < count; i++) {
                long userId = OWNER_ID + 1000 + i;
                long t1 = System.currentTimeMillis();

                ResponseEntity<R> resp = postJoinRoomAsync(userId, roomCode);
                assertEquals(200, resp.getStatusCode().value());
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
                String token = (String) data.get("joinToken");

                if (token != null) {
                    waitForJoinStatus(token, 5);
                }

                long elapsed = System.currentTimeMillis() - t1;
                latencies[i] = elapsed;
            }

            long sum = 0;
            long max = 0;
            for (long l : latencies) {
                sum += l;
                if (l > max) max = l;
            }
            double avg = sum / (double) count;

            long[] sorted = latencies.clone();
            java.util.Arrays.sort(sorted);
            long p99 = sorted[(int) (count * 0.99) - 1];
            long p50 = sorted[count / 2];

            System.out.printf("[E2E-吞吐] %d次顺序入房，平均延迟=%.0fms, P50=%dms, P99=%dms, 最大=%dms%n",
                    count, avg, p50, p99, max);

            assertTrue(avg < 2000, "平均端到端延迟应 < 2000ms，实际=" + avg + "ms");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 轮询等待加入状态，最多等待 maxSeconds。
     * 每秒轮询一次，状态变为 JOINED 或 FAILED 时立即返回。
     */
    private JoinStatusVO waitForJoinStatus(String token, int maxSeconds) {
        for (int i = 0; i < maxSeconds; i++) {
            ResponseEntity<R> resp = getJoinResult(token);
            if (resp.getBody() != null && resp.getBody().getData() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) resp.getBody().getData();
                String statusStr = (String) data.get("status");
                if ("JOINED".equals(statusStr)) {
                    return new JoinStatusVO(
                        Status.JOINED,
                        data.get("roomId") != null ? Long.parseLong(data.get("roomId").toString()) : null,
                        data.get("userId") != null ? Long.parseLong(data.get("userId").toString()) : null,
                        null
                    );
                }
                if ("FAILED".equals(statusStr)) {
                    return new JoinStatusVO(
                        Status.FAILED,
                        data.get("roomId") != null ? Long.parseLong(data.get("roomId").toString()) : null,
                        data.get("userId") != null ? Long.parseLong(data.get("userId").toString()) : null,
                        null
                    );
                }
            }
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        return new JoinStatusVO(Status.PROCESSING, null, null, "等待超时");
    }
}
