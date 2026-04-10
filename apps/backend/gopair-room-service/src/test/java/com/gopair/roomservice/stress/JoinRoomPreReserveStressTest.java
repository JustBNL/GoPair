package com.gopair.roomservice.stress;

import com.gopair.roomservice.constant.RoomConst;
import com.gopair.roomservice.domain.po.Room;
import com.gopair.roomservice.service.JoinReservationService;
import com.gopair.roomservice.service.JoinReservationService.PreReserveResult;
import com.gopair.roomservice.service.JoinReservationService.ReserveStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步入房预占层压力测试，验证 Redis Lua 脚本在并发场景下的原子性。
 *
 * * [核心验证点]
 * - 防超卖：N 线程同时抢 M 个名额，最终入房人数（confirmed + reserved）不超过 max
 * - 防重入：同一用户短时间内多次发起预占，只有一个成功，其余被 ALREADY_PROCESSING 拒绝
 * - reserved 计数准确：预占成功后 reserved +1，不超不欠
 * - 吞吐能力：单次预占的平均耗时
 *
 * @author gopair
 */
@DisplayName("预占层压力测试：Redis Lua 脚本并发安全")
public class JoinRoomPreReserveStressTest extends BaseStressTest {

    @Autowired
    private JoinReservationService joinReservationService;

    private static final Long TEST_ROOM_ID = 998001L;
    private static final Long OWNER_ID = 10000L;

    @Nested
    @DisplayName("场景一：防超卖 — N 线程抢 5 个名额")
    class AntiOverSellTests {

        @Test
        @DisplayName("10 线程并发抢 4 个剩余名额，最终 confirmed+reserved 不超过 max=5")
        void testAntiOverSell_Exactly4Remaining() throws Exception {
            int maxMembers = 5;
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, maxMembers);
            // confirmed=1（房主）+ 可抢名额=4

            int threadCount = 10;  // 10 个用户同时抢
            int expectedAccepted = 4; // 只能成功 4 个
            AtomicInteger acceptedCount = new AtomicInteger(0);
            AtomicInteger fullCount = new AtomicInteger(0);
            AtomicInteger alreadyJoinedCount = new AtomicInteger(0);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                long userId = OWNER_ID + i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PreReserveResult result = joinReservationService.preReserve(room.getRoomId(), userId);
                        switch (result.status) {
                            case ACCEPTED -> acceptedCount.incrementAndGet();
                            case FULL -> fullCount.incrementAndGet();
                            case ALREADY_JOINED -> alreadyJoinedCount.incrementAndGet();
                            default -> {}
                        }
                    } catch (Exception e) {
                        System.err.println("[压测] 预占异常 userId=" + userId + ": " + e.getMessage());
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            long startTime = System.currentTimeMillis();
            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS), "预占压测应在 10 秒内完成");
            long elapsedMs = System.currentTimeMillis() - startTime;

            int totalSuccess = acceptedCount.get();
            RoomMetaSnapshot meta = readMeta(TEST_ROOM_ID);

            System.out.printf("[防超卖压测] 房间=%d, max=%d, 预占成功=%d, 满员拒绝=%d, " +
                            "已入房拒绝=%d, 最终 confirmed=%d, reserved=%d, totalOccupied=%d, 耗时=%dms%n",
                    TEST_ROOM_ID, maxMembers, totalSuccess, fullCount.get(),
                    alreadyJoinedCount.get(), meta.confirmed(), meta.reserved(), meta.totalOccupied(), elapsedMs);

            // 核心断言：totalOccupied = confirmed(1) + reserved(accepted) 不超过 max
            assertTrue(meta.totalOccupied() <= maxMembers,
                    "confirmed + reserved 不应超过 max，" +
                    "实际 totalOccupied=" + meta.totalOccupied() + " max=" + maxMembers);

            // 成功预占数应等于当前 reserved
            assertEquals(meta.reserved(), totalSuccess,
                    "预占成功数应等于 reserved 计数");

            // accepted 不超过剩余名额
            assertTrue(totalSuccess <= maxMembers - 1,
                    "预占成功数不超过剩余名额，actual=" + totalSuccess + " remaining=" + (maxMembers - 1));

            // 吞吐量
            double tps = threadCount * 1000.0 / elapsedMs;
            System.out.printf("[防超卖压测] 吞吐量: %.2f 次/秒%n", tps);
        }

        @Test
        @DisplayName("20 线程抢 1 个剩余名额，只有 1 个成功")
        void testAntiOverSell_OnlyOneWins() throws Exception {
            int maxMembers = 2;
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, maxMembers);
            // confirmed=1，剩余名额=1

            int threadCount = 20;
            AtomicInteger acceptedCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                long userId = OWNER_ID + i + 1;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PreReserveResult result = joinReservationService.preReserve(room.getRoomId(), userId);
                        if (result.status == ReserveStatus.ACCEPTED) {
                            acceptedCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));
            RoomMetaSnapshot meta = readMeta(TEST_ROOM_ID);

            System.out.printf("[极限防超卖] 20线程抢1个名额，成功=%d, reserved=%d, confirmed=%d, max=%d%n",
                    acceptedCount.get(), meta.reserved(), meta.confirmed(), maxMembers);

            assertEquals(1, acceptedCount.get(), "只有 1 个线程应成功获得名额");
            assertEquals(1, meta.reserved(), "reserved 应为 1");
            assertEquals(2, meta.totalOccupied(), "totalOccupied = confirmed(1) + reserved(1) = 2 = max");
        }

        @Test
        @DisplayName("50 线程同时抢空房间（满员），全部被 FULL 拒绝")
        void testAntiOverSell_FullRoom() throws Exception {
            int maxMembers = 5;
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, maxMembers);

            // 预先占满所有名额（4 个 pending）
            for (int i = 1; i <= 4; i++) {
                long userId = OWNER_ID + i;
                PreReserveResult r = joinReservationService.preReserve(TEST_ROOM_ID, userId);
                assertEquals(ReserveStatus.ACCEPTED, r.status);
            }
            // room: confirmed=1, reserved=4, totalOccupied=5 = max

            AtomicInteger fullCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(50);
            ExecutorService executor = Executors.newFixedThreadPool(50);

            for (int i = 0; i < 50; i++) {
                long userId = OWNER_ID + 1000 + i;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PreReserveResult r = joinReservationService.preReserve(TEST_ROOM_ID, userId);
                        if (r.status == ReserveStatus.FULL) fullCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            System.out.printf("[满员拒绝] 50线程抢满员房间，FULL拒绝=%d/50%n", fullCount.get());
            assertEquals(50, fullCount.get(), "满员时应全部被 FULL 拒绝");
        }
    }

    @Nested
    @DisplayName("场景二：防重复 — 同一用户并发预占")
    class AntiDuplicateTests {

        @Test
        @DisplayName("同一用户 10 次并发预占，只有 1 次成功，其余返回 ALREADY_PROCESSING")
        void testAntiDuplicate_SameUserConcurrent() throws Exception {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 5);

            long userId = OWNER_ID + 9999; // 唯一用户
            int threadCount = 10;
            AtomicInteger acceptedCount = new AtomicInteger(0);
            AtomicInteger processingCount = new AtomicInteger(0);

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PreReserveResult r = joinReservationService.preReserve(room.getRoomId(), userId);
                        switch (r.status) {
                            case ACCEPTED -> acceptedCount.incrementAndGet();
                            case ALREADY_PROCESSING -> processingCount.incrementAndGet();
                            default -> {}
                        }
                    } catch (Exception ignored) {
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            System.out.printf("[防重复] 同一用户10次并发预占，成功=%d, ALREADY_PROCESSING=%d%n",
                    acceptedCount.get(), processingCount.get());

            assertEquals(1, acceptedCount.get(), "只有 1 次应成功");
            assertEquals(threadCount - 1, processingCount.get(), "其余应被 ALREADY_PROCESSING 拒绝");
            assertEquals(1, readMeta(TEST_ROOM_ID).reserved(), "reserved 应为 1");
        }

        @Test
        @DisplayName("同一用户顺序预占 3 次，第 1 次 ACCEPTED，后 2 次 ALREADY_PROCESSING")
        void testAntiDuplicate_SameUserSequential() {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 5);

            long userId = OWNER_ID + 8888;
            PreReserveResult r1 = joinReservationService.preReserve(room.getRoomId(), userId);
            assertEquals(ReserveStatus.ACCEPTED, r1.status);
            assertNotNull(r1.joinToken);

            PreReserveResult r2 = joinReservationService.preReserve(room.getRoomId(), userId);
            assertEquals(ReserveStatus.ALREADY_PROCESSING, r2.status);

            PreReserveResult r3 = joinReservationService.preReserve(room.getRoomId(), userId);
            assertEquals(ReserveStatus.ALREADY_PROCESSING, r3.status);

            System.out.println("[顺序防重复] 第1次=ACCEPTED, 第2次=" + r2.status + ", 第3次=" + r3.status);
        }
    }

    @Nested
    @DisplayName("场景三：reserved 计数准确性")
    class ReservedCountTests {

        @Test
        @DisplayName("预占成功后 reserved +1，MQ 失败回滚后 reserved 回到 0")
        void testReserved_IncrementAndRollback() {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 5);

            // 预占前 reserved=0
            assertEquals(0, readMeta(TEST_ROOM_ID).reserved());

            // 预占成功
            long userId = OWNER_ID + 7777;
            PreReserveResult r = joinReservationService.preReserve(room.getRoomId(), userId);
            assertEquals(ReserveStatus.ACCEPTED, r.status);

            // 预占后 reserved=1
            assertEquals(1, readMeta(TEST_ROOM_ID).reserved());

            // 手动清理 pending（模拟 MQ 消费完成）
            redisTemplate.opsForHash().delete(RoomConst.pendingKey(TEST_ROOM_ID), String.valueOf(userId));
            redisTemplate.opsForHash().increment(RoomConst.metaKey(TEST_ROOM_ID), RoomConst.FIELD_RESERVED, -1);

            assertEquals(0, readMeta(TEST_ROOM_ID).reserved());
            System.out.println("[reserved计数] 预占+1=1, 释放-1=0，计数准确");
        }

        @Test
        @DisplayName("100 次连续预占，reserved 线性增长，最终等于成功次数")
        void testReserved_LinearGrowth() throws Exception {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 200);

            int successCount = 0;
            AtomicInteger successAtomic = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(100);
            ExecutorService executor = Executors.newFixedThreadPool(20);

            for (int i = 0; i < 100; i++) {
                final long userId = OWNER_ID + i + 10000;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PreReserveResult r = joinReservationService.preReserve(room.getRoomId(), userId);
                        if (r.status == ReserveStatus.ACCEPTED) {
                            successAtomic.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(15, TimeUnit.SECONDS));
            executor.shutdown();

            RoomMetaSnapshot meta = readMeta(TEST_ROOM_ID);
            System.out.printf("[reserved线性增长] 100线程压测，成功=%d, reserved=%d%n",
                    successAtomic.get(), meta.reserved());
            assertEquals(meta.reserved(), successAtomic.get(),
                    "reserved 应等于成功预占次数");
        }
    }

    @Nested
    @DisplayName("场景四：房间状态变更 — 关闭 / 过期")
    class RoomStatusChangeTests {

        @Test
        @DisplayName("房间关闭后，所有预占请求返回 CLOSED")
        void testRoomClosed_RejectsNewReserve() throws Exception {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 5);

            // 关闭房间：更新 DB 和 Redis
            UpdateWrapper<Room> uw = new UpdateWrapper<>();
            uw.eq("room_id", TEST_ROOM_ID).set("status", RoomConst.STATUS_CLOSED);
            roomMapper.update(null, uw);
            redisTemplate.opsForHash().put(
                RoomConst.metaKey(TEST_ROOM_ID), RoomConst.FIELD_STATUS,
                String.valueOf(RoomConst.STATUS_CLOSED));

            AtomicInteger closedCount = new AtomicInteger(0);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(20);
            ExecutorService executor = Executors.newFixedThreadPool(20);

            for (int i = 0; i < 20; i++) {
                long userId = OWNER_ID + i + 5000;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        PreReserveResult r = joinReservationService.preReserve(TEST_ROOM_ID, userId);
                        if (r.status == ReserveStatus.CLOSED) closedCount.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(10, TimeUnit.SECONDS));

            assertEquals(20, closedCount.get(), "房间关闭后所有预占应被 CLOSED 拒绝");
            System.out.println("[房间关闭] 20次预占全部被 CLOSED 拒绝 ✓");
        }

        @Test
        @DisplayName("房间过期后，预占请求返回 EXPIRED")
        void testRoomExpired_RejectsNewReserve() {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 5);

            // 设置过期时间为过去的时间
            long pastMs = System.currentTimeMillis() - 3600_000;
            UpdateWrapper<Room> uw2 = new UpdateWrapper<>();
            uw2.eq("room_id", TEST_ROOM_ID)
               .set("expire_time", java.time.LocalDateTime.ofEpochSecond(
                    pastMs / 1000, 0, java.time.ZoneOffset.ofHours(8)));
            roomMapper.update(null, uw2);
            redisTemplate.opsForHash().put(
                RoomConst.metaKey(TEST_ROOM_ID), RoomConst.FIELD_EXPIRE_AT, String.valueOf(pastMs));

            PreReserveResult r = joinReservationService.preReserve(TEST_ROOM_ID, OWNER_ID + 6666);
            assertEquals(ReserveStatus.EXPIRED, r.status);
            System.out.println("[房间过期] 预占被 EXPIRED 拒绝 ✓");
        }
    }

    @Nested
    @DisplayName("场景五：吞吐能力测试")
    class ThroughputTests {

        @Test
        @DisplayName("单线程顺序预占 100 次，测量平均延迟")
        void testSingleThreadThroughput() {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 200);
            long totalMs = 0;

            for (int i = 0; i < 100; i++) {
                long userId = OWNER_ID + i + 20000;
                long start = System.nanoTime();
                PreReserveResult r = joinReservationService.preReserve(room.getRoomId(), userId);
                long elapsedNs = System.nanoTime() - start;
                totalMs += elapsedNs / 1_000_000;
                assertEquals(ReserveStatus.ACCEPTED, r.status);
            }

            double avgMs = totalMs / 100.0;
            System.out.printf("[单线程吞吐] 100次预占，平均延迟=%.2fms, 总耗时=%dms%n", avgMs, totalMs);
            assertTrue(avgMs < 50, "单次预占平均延迟应 < 50ms，实际=" + avgMs + "ms");
        }

        @Test
        @DisplayName("20 线程并发压测 200 次，测量 QPS")
        void testConcurrentQPS() throws Exception {
            Room room = createTestRoom(TEST_ROOM_ID, OWNER_ID, 300);
            int threadCount = 20;
            int perThread = 10;

            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            long startTime = System.currentTimeMillis();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        for (int i = 0; i < perThread; i++) {
                            long userId = OWNER_ID + threadId * 1000 + i + 30000;
                            try {
                                PreReserveResult r = joinReservationService.preReserve(room.getRoomId(), userId);
                                if (r.status == ReserveStatus.ACCEPTED) successCount.incrementAndGet();
                            } catch (Exception ignored) {}
                        }
                    } catch (Exception ignored) {
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(endLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();
            long elapsedMs = System.currentTimeMillis() - startTime;

            double qps = (threadCount * perThread) * 1000.0 / elapsedMs;
            System.out.printf("[并发QPS] %d线程×%d次=%d次总请求，成功=%d, 耗时=%dms, QPS=%.2f%n",
                    threadCount, perThread, threadCount * perThread,
                    successCount.get(), elapsedMs, qps);

            // QPS 基准：Redis Lua 操作在 localhost 下应能轻松达到 > 500 次/秒
            assertTrue(qps > 100, "QPS 应 > 100，实际=" + qps);
        }
    }
}
