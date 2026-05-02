# 消息服务集成测试报告

## 测试环境

| 配置项 | 值 |
|---|---|
| 数据库 | MySQL (`gopair_test`)，`@Transactional` 回滚 |
| Redis | 真实连接，DB 14，`@AfterEach flushDb()` 清理 |
| RabbitMQ | Mock（`@MockBean ConnectionFactory + RabbitTemplate`） |
| WebSocket | Mock（`@MockBean WebSocketMessageProducer`） |
| RestTemplate | `mockRestTemplate`（Service 层外部调用拦截），`realRestTemplate`（Controller 测试） |

---

## 测试模块

### 1. MessageServiceLifecycleIntegrationTest

**类路径：** `com.gopair.messageservice.service.MessageServiceLifecycleIntegrationTest`

**主要测试用例：**

| 用例 | 输入参数 | 预期结果 |
|---|---|---|
| textMessage_FullLifecycle | ROOM_ID=1, USER_A_ID=100, 文本消息 | 发送成功 → 分页查询有记录 → 最新消息非空 → 统计≥1 → 删除后 DB 无记录 |
| fileMessage_WebSocketPushVerification | ROOM_ID=1, USER_B_ID=200, 图片消息 | 发送成功 → WebSocket payload 字段正确 → 非发送者删除抛异常 → 发送者删除成功 |
| emojiAndReplyMessage_Lifecycle | USER_A+USER_B 在房间内，Emoji+回复消息 | Emoji 发送成功 → 回复关联 replyToId → 统计按类型正确 → 删除正常 |
| sendMessage_UserNotInRoom_Throws | USER_C_ID=300 不在房间内 | 抛出异常，DB 无新增消息 |
| getMessageById_NotFound_Throws | messageId=99999 | 抛出 MESSAGE_NOT_FOUND 异常 |
| getRoomMessages_EmptyRoom_ReturnsEmpty | roomId=99999 | 返回空分页结果 total=0 |
| sendMessage_ContentTooLong_Throws | 内容 3000 字符（超限 2000） | 抛出异常，DB 无新增记录 |

---

### 2. UserProfileFallbackServiceImplIntegrationTest

**类路径：** `com.gopair.messageservice.service.UserProfileFallbackServiceImplIntegrationTest`

**主要测试用例：**

| 用例 | 输入参数 | 预期结果 |
|---|---|---|
| mapperJoinHasNickname_NoFallback | MessageVO 已有 nickname（Alice/Bob） | 降级服务不触发，RestTemplate 未被调用 |
| mapperJoinEmpty_SharedTableFallback | senderNickname 为空，USER_A/B 预置到 app_user 表 | 从 app_user 表补全：AliceFromDB/BobFromDB |
| allLocalFallbackFailed_HttpFallback | USER_C 无本地数据，配置 HTTP stub | RestTemplate HTTP 补全：CharlieFromHttp |
| mixedProfiles_PartialFallback | USER_A 有 nickname，USER_C 无 | USER_A 保持原有，USER_C 从 HTTP 补全 |
| emptyMessageList_NoException | 空 List | 正常返回，不抛异常 |

---

### 3. MessageSentEventIntegrationTest

**类路径：** `com.gopair.messageservice.service.MessageSentEventIntegrationTest`

**主要测试用例：**

| 用例 | 输入参数 | 预期结果 |
|---|---|---|
| sendMessage_TriggersPublishEvent | USER_A_ID=100 在房间内发送文本 | `ApplicationEventPublisher.publishEvent` 被调用 1 次 |
| multipleMessages_MultipleEventPublishes | USER_A + USER_B 各发送一条 | publishEvent 被调用 2 次 |
| userNotInRoom_NoEventPublished | USER_A_ID=100 不在房间内 | 消息被拦截，无 publishEvent 调用 |

---

### 4. MessageControllerApiIntegrationTest

**类路径：** `com.gopair.messageservice.controller.MessageControllerApiIntegrationTest`

**主要测试用例：**

| 用例 | 输入参数 | 预期结果 |
|---|---|---|
| textMessage_ControllerFullLifecycle | USER_A 发送文本，通过 `realRestTemplate` HTTP | POST 200 → GET 分页 200 → GET 最新 200 → GET 统计 200 → DELETE 200 |
| fileMessage_ControllerDetailAndPermissionCheck | USER_B 发送图片，USER_A 尝试删除 | 详情查询 200 → Alice 删除返回非 200 → Bob 删除返回 200 |
| getRoomMessages_EmptyRoom_ReturnsEmpty | roomId=99999 | HTTP 200，返回空分页 |
| getMessageById_NotFound_ReturnsErrorCode | messageId=99999 | HTTP 200，业务码非 200 |
| sendMessage_UserNotInRoom_Blocked | USER_A 不在房间内 | HTTP 响应码非 200（USER_NOT_IN_ROOM） |

---

## 数据清理策略

- **MySQL：** 每个测试方法在 `@Transactional` 边界内执行，测试结束后自动回滚。
- **Redis：** `@AfterEach` 执行 `flushDb()` 清理 DB 14，确保测试间隔离。
- **RestTemplate stubs：** `@AfterEach` 调用 `MockRestTemplateConfig.clear()` 清理 HTTP stub。
