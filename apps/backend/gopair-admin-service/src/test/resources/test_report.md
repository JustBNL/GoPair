# GoPair Admin Service 测试报告

## 测试环境

- 数据库：`gopair_test`（MySQL 8.0）
- 测试策略：重集成测试（真实 MySQL + `@Transactional` 回滚）
- 无 Redis 依赖（无需清理）
- 基类：`BaseIntegrationTest`

---

## 测试模块总览

| 测试类 | 类型 | 父类/注解 | 测试数 | 描述 |
|--------|------|-----------|--------|------|
| `AdminServiceLifecycleIntegrationTest` | 集成测试 | `BaseIntegrationTest` | ~25 | 全链路 Service 层测试，真实 DB 读写 |
| `AdminAuthControllerTest` | Controller 切片 | `@WebMvcTest` + `@MockBean` | 6 | 登录接口参数解析与响应封装 |
| `AdminManageControllerTest` | Controller 切片 | `@WebMvcTest` + `@MockBean` | ~20 | 各管理接口参数解析与响应封装 |
| `AdminJwtUtilsUnitTest` | 单元测试 | 无 Spring | 10 | JWT 工具类静态方法 |

---

## 模块 1：AdminServiceLifecycleIntegrationTest

继承 `BaseIntegrationTest`，使用真实 MySQL + `@Transactional` 自动回滚。

### 测试流 A：认证登录 + 用户管理

| 用例 | 输入参数 | 预期结果 |
|------|----------|----------|
| `login_WithValidCredentials_ShouldReturnToken` | username=`testadmin`, password=`testpass123` | 返回 `LoginResult(token, adminId, username, nickname)` |
| `login_WithNonexistentUser_ShouldThrow` | username=`nonexistent`, password=`anypass` | 抛出 `IllegalArgumentException`，消息含"不存在" |
| `login_WithWrongPassword_ShouldThrow` | username=`testadmin`, password=`wrongpassword` | 抛出 `IllegalArgumentException`，消息含"密码错误" |
| `login_WithDisabledAccount_ShouldThrow` | username=`disabled_admin`, status=`1` | 抛出 `IllegalArgumentException`，消息含"停用" |
| `getUserPage_WithPaginationAndKeyword_ShouldReturnFilteredResults` | pageNum=`1`, pageSize=`10`, keyword=`Alice` | 返回包含目标用户的分页结果 |
| `getUserDetail_WhenUserExists_ShouldReturnFullDetail` | userId | 返回 user + roomCount + ownedRoomCount |
| `getUserDetail_WhenUserNotFound_ShouldThrow` | userId=`999999` | 抛出 `IllegalArgumentException` |
| `disableUser_ShouldUpdateStatusAndWriteAuditLog` | userId | user.status 变为 `'1'` |
| `enableUser_ShouldUpdateStatus` | userId | user.status 变回 `'0'` |

### 测试流 B：房间管理 + 文件管理

| 用例 | 输入参数 | 预期结果 |
|------|----------|----------|
| `getRoomPage_WithFilters_ShouldReturnFilteredResults` | pageNum=`1`, pageSize=`10`, status=`0`/`1`, keyword | 按条件返回过滤后分页结果 |
| `getRoomDetail_WhenRoomExists_ShouldReturnFullDetail` | roomId | 返回 room + members(2) + userMap |
| `getRoomDetail_WhenRoomNotFound_ShouldThrow` | roomId=`999999` | 抛出 `IllegalArgumentException` |
| `closeRoom_ShouldUpdateStatusAndWriteAuditLog` | roomId | room.status 变为 `1` |
| `getFilePage_WithFilters_ShouldReturnFilteredResults` | roomId / keyword | 按条件返回过滤后分页结果 |
| `deleteFile_ShouldRemoveRecordAndWriteAuditLog` | fileId | `room_file` 表中对应记录被物理删除 |
| `deleteFile_WhenNotFound_ShouldThrow` | fileId=`999999` | 抛出 `IllegalArgumentException` |

### 测试流 C：消息、通话、仪表盘、审计日志

| 用例 | 输入参数 | 预期结果 |
|------|----------|----------|
| `getMessagePage_WithFilters_ShouldReturnFilteredResults` | roomId / keyword | 按条件返回过滤后分页结果 |
| `getMessageByRoom_WithPaginationAndKeyword_ShouldReturnFilteredResults` | roomId, pageNum=`1`, pageSize=`10` | 返回房间内消息列表 |
| `getVoiceCallPage_WithFilters_ShouldReturnFilteredResults` | roomId / status | 按条件返回过滤后分页结果 |
| `getVoiceCallById_WithExistingCall_ShouldReturnCall` | callId（存在/不存在） | 存在返回 VoiceCall，不存在返回 null |
| `getParticipants_ShouldReturnParticipantList` | callId | 返回参与者列表，size=`2` |
| `getStats_ShouldReturnAggregatedStatistics` | 无 | 返回 DashboardStats（todayMessages 硬编码为 0） |
| `getAuditLogPage_WithFilters_ShouldReturnFilteredResults` | adminId / operation / targetType | 按条件返回过滤后分页结果 |

---

## 模块 2：AdminAuthControllerTest

使用 `@WebMvcTest` + `@MockBean`（无 DB），测试 `POST /admin/auth/login`。

| 用例 | 输入参数 | 预期结果 |
|------|----------|----------|
| `login_WithValidCredentials_ShouldReturn200WithToken` | username=`admin`, password=`admin123` | `code=200`, `data.token=mock.jwt.token` |
| `login_WithNonexistentUser_ShouldReturn401` | username=`nonexistent` | `code=401`, `msg=管理员账号不存在` |
| `login_WithWrongPassword_ShouldReturn401` | password=`wrongpassword` | `code=401`, `msg=密码错误` |
| `login_WithDisabledAccount_ShouldReturn401` | username=`admin`, status=`1` | `code=401`, `msg=管理员账号已被停用` |
| `login_MissingUsername_ShouldReturn400` | 仅传 password | `status=400` |
| `login_MissingPassword_ShouldReturn400` | 仅传 username | `status=400` |

---

## 模块 3：AdminManageControllerTest

使用 `@WebMvcTest` + `@MockBean`（无 DB），覆盖所有管理接口。

| 控制器 | 端点 | 用例数 | 覆盖场景 |
|--------|------|--------|----------|
| UserManageController | `GET /admin/users/page` | 2 | 分页/无keyword |
| UserManageController | `GET /admin/users/{userId}` | 2 | 用户存在/不存在 |
| UserManageController | `POST /admin/users/{userId}/disable` | 2 | 正常/用户不存在 |
| UserManageController | `POST /admin/users/{userId}/enable` | 1 | 正常启用 |
| RoomManageController | `GET /admin/rooms/page` | 1 | 分页+过滤 |
| RoomManageController | `GET /admin/rooms/{roomId}` | 2 | 房间存在/不存在 |
| RoomManageController | `POST /admin/rooms/{roomId}/close` | 2 | 正常/房间不存在 |
| FileManageController | `GET /admin/files/page` | 1 | 分页 |
| FileManageController | `GET /admin/files/{fileId}` | 2 | 文件存在/不存在 |
| FileManageController | `POST /admin/files/{fileId}/delete` | 2 | 正常/不存在 |
| VoiceCallController | `GET /admin/voice-calls/page` | 1 | 分页 |
| VoiceCallController | `GET /admin/voice-calls/{callId}` | 2 | 通话存在/不存在 |
| VoiceCallController | `GET /admin/voice-calls/{callId}/participants` | 1 | 返回参与者列表 |
| DashboardController | `GET /admin/dashboard/stats` | 1 | 返回统计数据 |
| AuditLogController | `GET /admin/audit-logs/page` | 1 | 分页+多条件过滤 |
| MessageManageController | `GET /admin/messages/page` | 1 | 分页 |
| MessageManageController | `GET /admin/messages/room/{roomId}` | 1 | 按房间查询 |

---

## 模块 4：AdminJwtUtilsUnitTest

纯单元测试，无 Spring 上下文，直接测试静态方法。

| 用例 | 输入参数 | 预期结果 |
|------|----------|----------|
| `generateToken_ShouldContainCorrectClaims` | username=`admin`, adminId=`1` | Token 非空，Claims 正确 |
| `generateToken_Twice_ShouldBothBeValid` | 两次相同参数 | 两次生成的 Token 均有效 |
| `validateToken_WithValidToken_ShouldReturnTrue` | 有效 Token | `true` |
| `validateToken_WithExpiredToken_ShouldReturnFalse` | expiration=-1000ms | `false` |
| `validateToken_WithForgedToken_ShouldReturnFalse` | 末尾篡改 Token | `false` |
| `validateToken_WithDifferentSecret_ShouldReturnFalse` | 正确 Token + 错误 Secret | `false` |
| `validateToken_WithNullSecret_ShouldReturnFalse` | null Secret | `false`（异常被内部吞掉） |
| `generateToken_WithNullSecret_ShouldThrowException` | null Secret | `IllegalArgumentException` |
| `generateToken_WithShortSecret_ShouldThrowException` | Secret < 64 字节 | `IllegalArgumentException` |
| `getUsernameFromToken_WithValidToken_ShouldReturnCorrectUsername` | username=`superadmin` | `superadmin` |
| `getAdminIdFromToken_WithValidToken_ShouldReturnCorrectId` | adminId=`42` | `"42"` |
