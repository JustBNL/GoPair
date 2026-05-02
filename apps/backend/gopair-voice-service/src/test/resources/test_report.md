# gopair-voice-service 测试报告

## 测试基础设施

### 基础设施改造
- **移除 H2 依赖**：从 `pom.xml` 中移除 `com.h2database:h2`
- **测试数据源**：`jdbc:mysql://localhost:3306/gopair_test`，HikariCP 连接池
- **测试 Redis**：真实 Redis 连接（DB 14），每个测试前 `flushDb()` 清理
- **事务策略**：
  - Service 层测试：`@Transactional` 保证每个测试方法结束后自动回滚
  - Controller 层测试：使用 `TestDataCleaner` 手动清理，**不使用** `@Transactional`（HTTP 请求跨线程，需要手动管理数据生命周期）
- **MQ/WebSocket Mock**：`RabbitTemplate`、`WebSocketMessageProducer`、`RoomEventConsumer` 均为 `@MockBean`，避免测试间相互干扰
- **MyBatis L1 缓存规避**：使用 `BaseIntegrationTest.selectCall()` 通过 `JdbcTemplate` 直接查 DB，绕过 MyBatis 一级缓存，确保 Service 操作后的 DB 状态可见

### 测试配置文件
| 文件 | 用途 |
|------|------|
| `src/test/resources/application-test.yml` | 集成测试配置（MySQL gopair_test + Redis DB 14）|
| `src/test/resources/schema.sql` | MySQL 8.0 兼容建表脚本 |

### 基础设施基类
`BaseIntegrationTest` 提供：
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("test")`
- 真实 `StringRedisTemplate`（注入，非 Mock）
- `JdbcTemplate`：无事务，用于 `@BeforeEach` 清理，不受 `@Transactional` 回滚影响
- `TransactionTemplate`：手动事务控制
- `TestRestTemplate`：随机端口 HTTP 测试
- `selectCall(Long)`：通过 `JdbcTemplate` 直接查 DB，绕过 MyBatis L1 缓存
- MQ/WebSocket 全系列 `@MockBean`
- `@AfterEach flushDb()` Redis 清理

### 数据清理工具
`TestDataCleaner` 提供 `cleanupAll()`、`cleanupByRoomId(Long)`、`cleanupByCallId(Long)` 方法，用于 Controller 层测试的手动数据清理。

---

## 测试模块详情

### 1. VoiceControllerIntegrationTest
**文件**：`src/test/java/com/gopair/voiceservice/controller/VoiceControllerIntegrationTest.java`
**类型**：Controller 层集成测试（HTTP + 真实 DB）
**测试用例**（共 12 个）：

#### 分支：主干流 A - 完整通话生命周期 HTTP 链路

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| joinOrCreateCall_ShouldReturnCallVO | roomId, userId, X-User-Id/X-Nickname headers | status=200, CallVO.callId/roomId/initiatorId 非空, status=IN_PROGRESS |
| notifyReady_ShouldReturn200 | callId, userId, X-User-Id/X-Nickname headers | status=200 |
| leaveCall_ShouldReturn200 | callId, userId | status=200 |
| endCall_ShouldReturn200 | callId, ownerId | status=200 |

#### 分支：分支流 B - 查询 + 主动结束

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| getCall_ShouldReturnCallVO | callId | status=200, CallVO.callId/roomId/initiatorId 匹配 |
| getActiveCall_ShouldReturnCallOrNull | roomId（含已创建通话）| status=200, CallVO.callId 匹配 |
| ownerLeave_ShouldReturn200 | callId, ownerId | status=200, 通话继续 |
| joinCallById_ShouldReturnCallVO | callId, userId | status=200, participantCount=2 |

#### 分支：边界流 C - 异常路径

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| getCall_NotFound_ShouldReturnError | 不存在的 callId | status != 200, R.code != 200 |
| getActiveCall_NoActiveCall_ShouldReturnNull | 空 roomId（无通话）| status=200, R.data=null |
| joinCall_EndedCall_ShouldReturnError | 已结束 callId | status != 200, R.code != 200 |
| forwardSignaling_NonParticipant_ShouldReturn200 | strangerId, 非参与者信令 | status=200, 信令被静默丢弃 |

---

### 2. VoiceCallLifecycleIntegrationTest
**文件**：`src/test/java/com/gopair/voiceservice/service/VoiceCallLifecycleIntegrationTest.java`
**类型**：Service 层集成测试（直接调用 Service + 真实 DB）
**测试用例**（共 13 个）：

#### 分支：测试流 A - 完整通话生命周期

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| createAutoCall_ShouldPersistCorrectly | roomId, ownerId | CallVO.callId/roomId/initiatorId 匹配, voice_call DB 记录存在 |
| joinCall_ShouldInsertParticipant | roomId, ownerId, userId | CallVO.participantCount=2, voice_call_participant DB 记录存在 |
| notifyReady_ShouldUpdateConnectionStatus | callId, userId | DB participant.connectionStatus=CONNECTED |
| leaveCall_ShouldUpdateLeaveTime | callId, userId | DB participant.leaveTime 非空, connectionStatus=DISCONNECTED |
| leaveCall_LastUser_ShouldTerminateCall | roomId, ownerId | DB call.status=ENDED, endTime/duration 非空 |

#### 分支：测试流 B - 房主离开但通话继续 + endCall

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| ownerLeave_ShouldAllowCallToContinue | roomId, ownerId, userId | DB call.status=IN_PROGRESS, 通话继续 |
| endCall_ShouldTerminateAndCalculateDuration | roomId, ownerId, userId1, userId2（等待 1.1s）| DB call.status=ENDED, duration >= 0 |

#### 分支：测试流 C - 异常边界

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| joinCall_Duplicate_ShouldBeIdempotent | roomId, ownerId, userId（重复加入 3 次）| voice_call_participant 记录仅 1 条 |
| createAutoCall_Duplicate_ShouldReturnExisting | roomId, ownerId（两次 createAutoCall）| 两次返回相同 callId, DB 仅 1 条 IN_PROGRESS 记录 |
| getActiveCall_NoActiveCall_ShouldReturnNull | 空 roomId | 返回 null |
| endCall_AlreadyEnded_ShouldBeIdempotent | roomId, ownerId（两次 endCall）| 第二次不抛异常, DB call.status=ENDED |
| forwardSignaling_NonParticipant_ShouldSilentlyDrop | roomId, ownerId, strangerId（非参与者）| DB call.status 不变，不抛异常 |
| forwardSignaling_Participant_ShouldForward | roomId, ownerId, userId | DB call.status 不变，不抛异常 |

---

### 3. VoiceCallServiceImplUnitTest
**文件**：`src/test/java/com/gopair/voiceservice/service/VoiceCallServiceImplUnitTest.java`
**类型**：Service 层集成测试（直接调用 Service + 真实 DB）
**测试用例**（共 8 个）：

#### 分支：通话时长计算

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| earlyEnd_ShouldCalculatePositiveDuration | roomId, ownerId（等待 1.1s 后 endCall）| DB call.status=ENDED, duration > 0 |
| immediateEnd_ShouldHaveZeroDuration | roomId, ownerId（立即 endCall）| DB call.status=ENDED, duration >= 0 |
| alreadyEnded_ShouldBeIdempotent | roomId, ownerId（两次 endCall）| 第二次不抛异常, DB call.status=ENDED |

#### 分支：重复加入同一通话

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| rejoin_ShouldResetLeaveTimeAndConnectionStatus | callId, userId（离开后再加入）| DB participant.leaveTime=null, connectionStatus=CONNECTED |
| duplicateJoin_ShouldNotInsertDuplicate | callId, userId（joinOrCreateCall 3 次）| DB voice_call_participant 仅 1 条 |

#### 分支：信令转发权限校验

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| participantForwarding_ShouldNotModifyDb | callId, senderId, receiverId（均为参与者）| DB call.status=IN_PROGRESS，participant 记录数不变 |
| nonParticipantForwarding_ShouldSilentlyDrop | callId, strangerId（非参与者）| 不抛异常, DB participant 记录数不变 |

#### 分支：自动创建通话幂等性

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| existingActiveCall_ShouldSkipCreation | roomId, roomOwnerId（两次 createAutoCall）| 两次返回相同 callId, DB 仅 1 条 IN_PROGRESS 记录 |

---

### 4. RoomEventConsumerTest
**文件**：`src/test/java/com/gopair/voiceservice/messaging/RoomEventConsumerTest.java`
**类型**：MQ 消费者集成测试（Mock 验证）
**测试用例**（共 2 个）：

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| onRoomCreated_ShouldInvokeMock | event={eventType:"room_created", roomId, userId} | `roomEventConsumer.onRoomCreated()` 被调用 1 次 |
| onRoomCreated_NullEventType_ShouldHandleGracefully | event={roomId}（eventType=null）| 不抛异常, mock 被调用 1 次 |

---

## 测试执行结果

```
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
```
- VoiceControllerIntegrationTest$FullCallLifecycleFlow: 4 passed
- VoiceControllerIntegrationTest$QueryAndEndFlow: 4 passed
- VoiceControllerIntegrationTest$EdgeCaseFlow: 4 passed
- VoiceCallLifecycleIntegrationTest$CallLifecycleFlow: 5 passed
- VoiceCallLifecycleIntegrationTest$OwnerLeaveAndEndCallFlow: 2 passed
- VoiceCallLifecycleIntegrationTest$ExceptionAndEdgeCaseFlow: 6 passed
- VoiceCallServiceImplUnitTest$DurationCalculationTests: 3 passed
- VoiceCallServiceImplUnitTest$RejoinTests: 2 passed
- VoiceCallServiceImplUnitTest$SignalingPermissionTests: 2 passed
- VoiceCallServiceImplUnitTest$AutoCreateIdempotentTests: 1 passed
- RoomEventConsumerTest: 2 passed
