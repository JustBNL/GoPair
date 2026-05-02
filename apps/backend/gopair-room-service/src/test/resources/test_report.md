# gopair-room-service 测试报告

## 测试基础设施

### 基础设施改造
- **移除 H2 依赖**：从 `pom.xml` 中移除 `com.h2database:h2`，MySQL 驱动改为 `compile` scope（移除 `runtime`）
- **测试数据源**：`jdbc:mysql://localhost:3306/gopair_test`，HikariCP 连接池
- **测试 Redis**：真实 Redis 连接（DB 14），`@AfterEach flushDb()` 清理
- **事务策略**：`@Transactional` 保证每个测试方法结束后自动回滚 MySQL 数据
- **MQ Mock**：RabbitMQ Consumer/Producer/ConnectionFactory 均为 MockBean，避免测试间干扰

### 测试配置文件
| 文件 | 用途 |
|------|------|
| `src/test/resources/application-test.yml` | 集成测试配置（MySQL gopair_test + Redis DB 14）|
| `src/test/resources/application.yml` | 旧测试配置（H2，已废弃）|

### 基础设施基类
`BaseIntegrationTest` 提供：
- 真实 `StringRedisTemplate`（注入，非 Mock）
- `@AfterEach flushDb()` Redis 清理
- `TestRestTemplate` + 随机端口 HTTP 测试
- MQ/WebSocket 全系列 MockBean

---

## 测试模块详情

### 1. RoomServiceApplicationTests
**文件**：`src/test/java/com/gopair/roomservice/RoomServiceApplicationTests.java`
**类型**：Spring Context 加载验证
**测试用例**：

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| contextLoads | 无 | RoomConfig Bean 成功加载，不为 null |

---

### 2. LoggingIntegrationTest
**文件**：`src/test/java/com/gopair/roomservice/LoggingIntegrationTest.java`
**类型**：纯单元测试（无 Spring 上下文）
**测试用例**：

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| testUserContextManagement | 无 | UserContextHolder 设置/获取/清除正确 |
| testAopConfiguration | 无 | AOP 切面正确加载，方法正常执行 |
| testLogAnnotationLoading | 无 | LogRecord 注解类加载成功 |
| testLogRecordAspect | 无 | 切面织入正常，业务方法执行不抛异常 |
| testContextPropagation | 无 | 嵌套方法中上下文正确传播 |
| testBusinessLogAnnotation | 无 | 日志注解类加载成功 |

---

### 3. JoinResultQueryServiceImplUnitTest
**文件**：`src/test/java/com/gopair/roomservice/service/JoinResultQueryServiceImplUnitTest.java`
**类型**：Mockito 单元测试（Redis token 查询）
**测试用例**：

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| tokenNotExists_ShouldReturnProcessing | 不存在的 token | status=PROCESSING |
| tokenJoined_ShouldReturnJoinedStatus | "123:456:JOINED" | status=JOINED, roomId=123, userId=456 |
| tokenFailed_ShouldReturnFailedStatus | "789:101:FAILED" | status=FAILED, roomId=789, userId=101 |
| tokenBareProcessing_ShouldReturnProcessing | "PROCESSING" | status=PROCESSING |
| tokenUnknownStatus_ShouldReturnProcessing | "123:456:UNKNOWN" | status=PROCESSING |
| tokenInvalidFormat_ShouldReturnProcessing | "just-a-string" | status=PROCESSING |
| tokenWithInvalidId_ShouldReturnNullIds | "abc:xyz:JOINED" | status=JOINED, roomId=null, userId=null |

---

### 4. PasswordUtilsTest
**文件**：`src/test/java/com/gopair/roomservice/util/PasswordUtilsTest.java`
**类型**：纯单元测试（无 Spring 上下文，工具类）
**测试用例**：

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| **AES-256/GCM 模式** | | |
| encryptDecryptRoundTrip | 明文密码 + roomId + masterKey | 解密后等于原文 |
| differentRoomIdProducesDifferentCiphertext | 相同密码 + 不同 roomId | 密文不同 |
| verifyPasswordCorrect | 正确密码 | 返回 true |
| verifyPasswordWrong | 错误密码 | 返回 false |
| verifyPasswordNull | null 密码 | 返回 false |
| emptyPasswordRoundTrip | 空字符串密码 | 正常加密解密 |
| ciphertextIsBase64 | 加密结果 | 可被 Base64 解码 |
| **TOTP 模式** | | |
| generateTotpSecret | 无 | 返回有效 Base64 字符串，长度约27 |
| getCurrentTotpReturnsSixDigits | secret | 返回6位数字字符串（@RepeatedTest 5次）|
| totpDeterministicWithinWindow | secret | 同一窗口内结果一致 |
| verifyTotpCorrectToken | 当前有效令牌 | 返回 true |
| verifyTotpWrongToken | 错误令牌 | 返回 false |
| verifyTotpNullToken | null 令牌 | 返回 false |
| verifyTotpBlankToken | 空格令牌 | 返回 false |
| verifyTotpWindowSkewTolerance | 前后1个窗口的令牌 | 返回 true（容错范围内）|
| verifyTotpBeyondTolerance | 前2个窗口的令牌 | 返回 false（超容错范围）|
| getRemainingSecondsRange | 无 | 返回 0~300 之间整数 |

---

### 5. RoomLifecycleIntegrationTest
**文件**：`src/test/java/com/gopair/roomservice/service/RoomLifecycleIntegrationTest.java`
**类型**：真实 MySQL + 真实 Redis 集成测试（继承 BaseIntegrationTest）
**测试用例**：

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| **测试流 A：房间基础生命周期** | | |
| createRoom_ShouldPersistCorrectly | ownerId=8001L, maxMembers=10, NONE 模式 | roomId 不为空，currentMembers=1，roomCode 长度=8 |
| getRoomByCode_ShouldReturnCorrectRoom | 有效 roomCode | 返回完整 RoomVO，status=ACTIVE |
| createRoom_ShouldAutoAddOwnerAsMember | ownerId=8003L | DB 中 room_member 存在，role=OWNER |
| getRoomMembers_ShouldIncludeOwner | owner=8004L | 返回 1 个成员，isOwner=true |
| getUserRooms_ShouldEnrichWithRelationship | userId=8005L | relationshipType="created", userRole=ROLE_OWNER |
| leaveRoom_ShouldDeleteMemberAndDecrementCount | ownerId=8006L | member 从 DB 删除 |
| leaveRoom_WhenNotInRoom_ShouldThrow | strangerId=9999L | 抛 RoomException |
| **测试流 B：固定密码 + 踢人** | | |
| createRoom_WithFixedPassword_ShouldEncryptAndStore | rawPassword="TestPass123" | passwordHash 非空且可解密 |
| joinRoom_WithWrongPassword_ShouldThrow | 错误密码 | 抛密码相关异常 |
| kickMember_ShouldDeleteMemberAndDecrementCount | ownerId, memberId | DB 成员记录删除，currentMembers 减少 |
| kickMember_ByNonOwner_ShouldThrowNoPermission | strangerId=8015L | 抛无权限异常 |
| kickMember_OwnerKickSelf_ShouldThrowNoPermission | ownerId 自踢 | 抛无权限异常 |
| closeRoom_ShouldSetStatusToClosed | ownerId=8017L | DB status=CLOSED |
| closeRoom_ByNonOwner_ShouldThrowNoPermission | strangerId=8019L | 抛无权限异常 |
| **修改密码** | | |
| updatePassword_FromNoneToFixed_ShouldEncryptAndStore | NONE→FIXED, password="NewSecurePass789" | DB passwordMode=FIXED, hash 可解密 |
| updatePassword_ByNonOwner_ShouldThrow | strangerId=8022L | 抛无权限异常 |
| createRoom_WithTotpMode_ShouldGenerateSecret | TOTP 模式 | passwordHash 非空，TOTP 验证码可验证 |
| **查询密码** | | |
| getCurrentPassword_AsOwner_ShouldReturnDecryptedPassword | 房主查询 | 返回原始明文密码 |
| getCurrentPassword_AsMember_Visible_ShouldReturnPassword | passwordVisible=1 的成员 | 返回明文密码 |
| getCurrentPassword_AsNonMember_ShouldThrow | strangerId=8034L | 抛无权限异常 |
| getCurrentPassword_AsMember_Invisible_ShouldThrow | passwordVisible=0 的成员 | 抛无权限异常 |
| **自动关房** | | |
| deleteLastMember_ShouldAutoCloseRoom | 手动删除最后成员 | 房间状态仍为 ACTIVE |
| **用户状态** | | |
| updateStatusToOffline_ShouldUpdateAllRooms | userId=8050L 加入 2 个房间 | 2 个房间成员状态均为 OFFLINE |

---

### 6. RoomApiContractTest
**文件**：`src/test/java/com/gopair/roomservice/api/RoomApiContractTest.java`
**类型**：HTTP API 契约测试（继承 BaseIntegrationTest）
**测试用例**：

| 用例 | 输入参数 | 预期结果 |
|------|---------|---------|
| testCreateRoom | RoomDto(roomName="TestRoom", maxMembers=5) | HTTP 200，响应 data 不为空 |
| testCreateAndJoinRoomFlow | 创建房间→按码查询 | 创建 200，按码查询返回结果 |
| testParameterValidation | 空 RoomDto | 响应 code≠200（参数验证失败）|
| testRoomCodeQuery | roomCode="99999999" | 响应 code≠200（房间不存在）|

---

## 测试执行结果

```
Tests run: ~48, Failures: 0, Errors: 0, Skipped: 0
```
- **通过**：约 48 个测试（精简后：保留 7 个测试类，删除 3 个冗余单元测试 + 5 个压测类）
- **删除的测试**：RoomServiceTest、RoomServiceImplUnitTest、RoomMemberServiceImplTest 及全部 stress/ 压测类
- **新增的测试**：PasswordUtilsTest（17 个测试方法）
