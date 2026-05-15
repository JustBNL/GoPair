# gopair-room-service 测试进度与用例汇总

> 服务名称：gopair-room-service
> 基准路径：`apps/backend/gopair-room-service`
> 生成时间：2026-05-14

---

## 第一部分：测试代码结构布局 (Tree Diagram)

```
src/test/java/com/gopair/roomservice/
  ├─ base/
  │    └─ BaseIntegrationTest.java     (集成测试抽象基类，@Transactional + Redis flushDb())
  ├─ service/
  │    ├─ RoomServiceIntegrationTest.java  (Service 层集成测试，@Nested 分组)
  │    ├─ JoinResultQueryServiceImplUnitTest.java  (纯单元测试，@ExtendWith(MockitoExtension))
  │    └─ LoggingIntegrationTest.java   (纯单元测试，无 Spring 容器)
  ├─ util/
  │    └─ PasswordUtilsTest.java       (纯单元测试，无 Spring 容器)
  ├─ api/
  │    └─ RoomApiContractTest.java     (HTTP API 契约测试，唯一集成测试入口，@Nested 分组)
  └─ RoomServiceApplicationTests.java   (上下文加载测试)
```

### 继承结构

```
BaseIntegrationTest (abstract, @SpringBootTest, @Transactional)
  ├── RoomApiContractTest         (HTTP 层集成测试)
  └── RoomServiceIntegrationTest  (Service 层集成测试)

JoinResultQueryServiceImplUnitTest  (standalone, @ExtendWith(MockitoExtension)，无 Spring)
PasswordUtilsTest                    (standalone，纯 JUnit，无 Spring)
LoggingIntegrationTest               (standalone，纯 JUnit，无 Spring)
RoomServiceApplicationTests          (@SpringBootTest(webEnvironment = NONE)，无继承)
```

### 脏数据清理机制

- **MySQL**：`BaseIntegrationTest` 的 `@Transactional` 注解确保每个测试方法结束后自动回滚
- **Redis**：`BaseIntegrationTest` 的 `@AfterEach` 执行 `flushDb()` 清空 Redis DB 14
- **分页测试**：`GetMyRoomsTests` 使用 `@Rollback(false)` 保持数据积累，以验证分页逻辑

---

## 第二部分：接口集成测试用例 (Integration Tests)

### 接口：POST /room (创建房间)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：创建无密码房间 | `{"roomName":"测试房间_xxx","maxMembers":10,"passwordMode":0}` | `{"code":200,"data":{"roomId":1,"roomCode":"8位码","status":0,...}}` | `room` 表插入一条记录；`room_member` 表插入一条房主记录（自动入房）；`current_members=1` |
| 成功路径：创建固定密码房间 | `{"roomName":"密码房间","maxMembers":10,"passwordMode":1,"rawPassword":"P@ss_xxx","passwordVisible":1}` | `{"code":200}` | `room` 表插入记录，`password_hash` 为 AES-256 加密后的密文（不同于原始密码） |
| 成功路径：创建 TOTP 动态密码房间 | `{"roomName":"TOTP房间","maxMembers":10,"passwordMode":2}` | `{"code":200,"data":{"passwordMode":2}}` | `room` 表插入记录，`password_hash` 为 16 字符 base32 TOTP secret |
| 参数校验：roomName 为空 | `{"roomName":"","maxMembers":10}` | 400 或 `{"code":...}` | 无 DB/Redis 写入 |
| 参数校验：maxMembers 小于 2 | `{"maxMembers":1}` | 400 或 `{"code":...}` | 无 DB/Redis 写入 |
| 参数校验：roomName 超过 50 字符 | `{"roomName":"测试".repeat(50)}` | 400 或 `{"code":...}` | 无 DB/Redis 写入 |
| 参数校验：passwordMode 非法值 | `{"passwordMode":99}` | 400 或 `{"code":...}` | 无 DB/Redis 写入 |
| 参数校验：rawPassword 长度不足 4 | `{"passwordMode":1,"rawPassword":"123"}` | 400 或 `{"code":...}` | 无 DB/Redis 写入 |
| 无认证：未提供 X-User-Id | 同上，无 Header | 401 或 `{"code":...}` | 无 DB/Redis 写入 |

---

### 接口：POST /room/join/async (异步申请加入房间)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：无密码房间 → 返回 joinToken | `{"roomCode":"有效8位码"}` | `{"code":200,"data":{"joinToken":"xxx","message":"..."}}` | Redis 写入 `join:{token}`（PROCESSING）+ `room:{roomId}:pending` 预占记录 |
| 成功路径：固定密码房间 + 正确密码 → 返回 joinToken | `{"roomCode":"有效8位码","password":"正确密码"}` | `{"code":200,"data":{"joinToken":"xxx"}}` | 同上 |
| 成功路径：无密码房间提供多余密码字段 → 被接受（密码字段可选） | `{"roomCode":"有效8位码","password":"ignored"}` | `{"code":200}` | 同上 |
| 错误路径：房间码不存在 → 拒绝 | `{"roomCode":"00000000"}` | `{"code":20201或20213}` | 无 DB/Redis 写入 |
| 错误路径：固定密码房间 + 错误密码 → PASSWORD_WRONG | `{"roomCode":"有效8位码","password":"错误密码"}` | `{"code":20213,"msg":"房间密码错误"}` | 无 DB/Redis 写入，抛出 `RoomException` |
| 错误路径：固定密码房间未提供密码 → PASSWORD_REQUIRED | `{"roomCode":"有效8位码"}`（passwordMode=1 时） | `{"code":20214,"msg":"请输入房间密码"}` | 无 DB/Redis 写入，抛出 `RoomException` |

---

### 接口：GET /room/join/result (查询加入结果)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：token 不存在 → PROCESSING（降级默认值） | `?token=nonexistent_token_xxx` | `{"code":200,"data":{"status":"PROCESSING"}}` | 无副作用，仅 Redis GET |
| 成功路径：joinToken 有效 → 返回 JOINED | `?token=<刚申请到的token>` | `{"code":200,"data":{"status":"JOINED"}}` | 无副作用 |

---

### 接口：POST /room/{roomId}/leave (离开房间)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房主离开 → true | `POST /room/{roomId}/leave`（房主身份） | `{"code":200,"data":true}` | `room_member` 表删除房主记录；`room` 表 `current_members` 减一 |
| 错误路径：非成员离开 → NOT_IN_ROOM | `POST /room/{roomId}/leave`（陌生人身份） | `{"code":20205,"msg":"不在房间中"}` | 无 DB/Redis 变更，抛出 `RoomException` |
| 错误路径：房间不存在 → ROOM_NOT_FOUND | `POST /room/999999/leave` | `{"code":20200,"msg":"房间不存在"}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：GET /room/code/{roomCode} (根据房间码查询房间)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：有效 roomCode → 返回房间信息 | `GET /room/code/有效8位码` | `{"code":200,"data":{"roomId":1,"roomName":"...","status":0}}` | 无写入副作用 |
| 错误路径：无效 roomCode → ROOM_CODE_INVALID | `GET /room/code/99999999` | `{"code":20201,"msg":"房间邀请码无效"}` | 无写入副作用，抛出 `RoomException` |

---

### 接口：GET /room/{roomId}/members (获取房间成员列表)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房间成员查询 → 返回成员列表（包含房主） | `GET /room/{roomId}/members`（房主身份） | `{"code":200,"data":[{"userId":1,"isOwner":true,"role":1}]}` | 无写入副作用 |
| 错误路径：非成员查询 → NOT_MEMBER | `GET /room/{roomId}/members`（非成员身份） | `{"code":20226,"msg":"您不在此房间中"}` | 无 DB/Redis 变更，抛出 `RoomException` |
| 错误路径：房间不存在 → ROOM_NOT_FOUND | `GET /room/999999/members` | `{"code":20200,"msg":"房间不存在"}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：POST /room/{roomId}/close (关闭房间)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房主关闭 → true，状态变为 CLOSED | `POST /room/{roomId}/close`（房主身份） | `{"code":200,"data":true}` | `room` 表 `status` 从 0 更新为 1（CLOSED） |
| 错误路径：非房主关闭 → NO_PERMISSION | `POST /room/{roomId}/close`（非房主身份） | `{"code":20215,"msg":"权限不足，仅房主可执行此操作"}` | 无 DB/Redis 变更，抛出 `RoomException` |
| 错误路径：房间不存在 → ROOM_NOT_FOUND | `POST /room/999999/close` | `{"code":20200}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：POST /room/{roomId}/renew (续期房间)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房主续期 ACTIVE 房间 → 房间延长过期时间 | `{"extendMinutes":60}`（房主身份） | `{"code":200,"data":{"roomId":...,"status":0}}` | `room` 表 `expire_time` 字段延长；`status` 更新为 0（ACTIVE） |
| 参数校验：extendMinutes 为 null → 400 | `{}` | 400 或 `{"code":...}` | 无 DB/Redis 变更 |
| 参数校验：extendMinutes 为 0 → 400 | `{"extendMinutes":0}` | 400 或 `{"code":...}` | 无 DB/Redis 变更 |
| 错误路径：非房主续期 → NO_PERMISSION | `{"extendMinutes":60}`（非房主） | `{"code":20215}` | 无 DB/Redis 变更，抛出 `RoomException` |
| 错误路径：房间不存在 → ROOM_NOT_FOUND | `{"extendMinutes":60}` | `{"code":20200}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：POST /room/{roomId}/reopen (重新开启房间)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：重新开启已关闭房间 → ACTIVE | `{"expireMinutes":60}`（房主身份） | `{"code":200,"data":{"status":0}}` | `room` 表 `status` 从 1 更新为 0（ACTIVE）；`expire_time` 重新设置 |
| 参数校验：expireMinutes 为 null → 400 | `{}` | 400 或 `{"code":...}` | 无 DB/Redis 变更 |
| 错误路径：非房主重新开启 → NO_PERMISSION | `{"expireMinutes":60}`（非房主） | `{"code":20215}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：GET /room/my (获取用户房间列表)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：查询用户房间列表 → 返回分页结果 | `GET /room/my?pageNum=1&pageSize=10` | `{"code":200,"data":{"total":3,"records":[...]}}` | 无写入副作用 |
| 成功路径：包含历史房间 → includeHistory=true | `GET /room/my?includeHistory=true` | `{"code":200}` | 无写入副作用 |

---

### 接口：PATCH /room/{roomId}/password (更新房间密码设置)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房主修改密码 NONE→FIXED | `{"mode":1,"rawPassword":"NewPass_xxx","visible":1}` | `{"code":200}` | `room` 表 `password_mode` 更新为 1；`password_hash` 更新为加密密文 |
| 成功路径：房主关闭密码 NONE→0 | `{"mode":0,"visible":0}` | `{"code":200}` | `room` 表 `password_mode` 更新为 0；`password_hash` 置空 |
| 错误路径：非房主修改密码 → NO_PERMISSION | `{"mode":1,"rawPassword":"..."}`（非房主） | `{"code":20215}` | 无 DB/Redis 变更，抛出 `RoomException` |
| 错误路径：房间不存在 → ROOM_NOT_FOUND | `{"mode":1,"rawPassword":"..."}` | `{"code":20200}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：PATCH /room/{roomId}/password/visibility (更新密码可见性)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房主切换可见性 → 0→1 | `{"visible":1}` | `{"code":200}` | `room` 表 `password_visible` 字段更新 |
| 参数校验：visible 为 null → 400 | `{}` | 400 或 `{"code":...}` | 无 DB/Redis 变更 |
| 错误路径：非房主更新可见性 → NO_PERMISSION | `{"visible":1}`（非房主） | `{"code":20215}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：GET /room/{roomId}/password/current (获取当前密码)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房主查询密码 → 返回解密明文 | `GET /room/{roomId}/password/current`（房主） | `{"code":200,"data":{"currentPassword":"P@ss_xxx"}}` | 无写入副作用；TOTP 模式下 Redis 查询当前令牌 |
| 成功路径：普通成员在密码可见时查询 → 返回解密明文 | `GET /room/{roomId}/password/current`（成员，visible=1） | `{"code":200,"data":{"currentPassword":"..."}}` | 无写入副作用 |
| 错误路径：非成员查询密码 → NOT_MEMBER | `GET /room/{roomId}/password/current`（非成员） | `{"code":20226}` | 无 DB/Redis 变更，抛出 `RoomException` |
| 错误路径：成员在密码不可见时查询 → NOT_MEMBER | `GET /room/{roomId}/password/current`（成员，visible=0） | `{"code":20226}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：DELETE /room/{roomId}/members/{userId} (踢出房间成员)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：房主踢出成员 → 成员被移除 | `DELETE /room/{roomId}/members/{memberId}`（房主身份） | `{"code":200}` | `room_member` 表删除该成员记录；`room` 表 `current_members` 减一；Redis 删除 `room:{roomId}:members` |
| 错误路径：非房主踢人 → NO_PERMISSION | `DELETE /room/{roomId}/members/{victimId}`（非房主） | `{"code":20215}` | 无 DB/Redis 变更，抛出 `RoomException` |
| 错误路径：房主自踢 → NO_PERMISSION | `DELETE /room/{roomId}/members/{ownerId}`（房主身份踢自己） | `{"code":20215}` | 无 DB/Redis 变更，抛出 `RoomException` |

---

### 接口：GET /room/{roomId}/members/{userId}/check (检查成员身份，**内部接口**)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：成员检查 → true（无需认证） | `GET /room/{roomId}/members/{ownerId}/check` | `{"code":200,"data":true}` | 无写入副作用 |
| 成功路径：非成员检查 → false | `GET /room/{roomId}/members/{strangerId}/check` | `{"code":200,"data":false}` | 无写入副作用 |
| 成功路径：房间不存在 → false（安全降级，不抛异常） | `GET /room/999999/members/1/check` | `{"code":200,"data":false}` | 无写入副作用 |

---

### 接口：GET /room/{roomId}/status (获取房间状态，**内部接口**)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：ACTIVE 房间 → status=0（无需认证） | `GET /room/{roomId}/status` | `{"code":200,"data":0}` | 无写入副作用 |
| 成功路径：CLOSED 房间 → status=1 | `GET /room/{roomId}/status` | `{"code":200,"data":1}` | 无写入副作用 |
| 成功路径：房间不存在 → null（安全降级） | `GET /room/999999/status` | `{"code":200,"data":null}` | 无写入副作用 |

---

### 接口：POST /room/{roomId}/members/batch (批量添加成员，**内部/压测接口**)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功路径：批量添加成员 → 返回成功数量 | `[1,2,3]` | `{"code":200,"data":3}` | `room_member` 表批量插入记录；`room` 表 `current_members` 累加 |
| 成功路径：空列表 → 返回 0 | `[]` | `{"code":200,"data":0}` | 无 DB 变更 |
| 成功路径：房间不存在 → 返回 0（静默降级） | `[1]` | `{"code":200,"data":0}` | 无 DB 变更 |

---

## 第三部分：单元测试用例汇总 (Unit Tests)

### 目标方法：`JoinResultQueryServiceImpl.queryByToken(String token)`

| 测试场景 | 输入参数 (对象) | 预期返回值 / 异常 (对象) |
| --- | --- | --- |
| token 不存在 | `String("nonexistent_token")` | `JoinStatusVO{status=PROCESSING}`（降级默认值） |
| token 存在且值含 JOINED | `String("1:1:JOINED")` | `JoinStatusVO{status=JOINED, roomId=1, userId=1}` |
| token 存在且值含 FAILED | `String("1:1:FAILED")` | `JoinStatusVO{status=FAILED, roomId=1, userId=1}` |
| token 格式有前缀值 | `String("1:1:PROCESSING")` | `JoinStatusVO{status=PROCESSING}` |
| token 格式未知状态值 | `String("1:1:UNKNOWN_STATUS")` | `JoinStatusVO{status=unknown_status, roomId=1, userId=1}` |
| token 格式无效（不含冒号） | `String("invalid_format")` | `JoinStatusVO{status=invalid_format, roomId=null, userId=null}` |
| token 中 roomId/userId 不是数字 | `String("abc:xyz:JOINED")` | 抛出 `NumberFormatException` 或降级 |

---

### 目标方法：`PasswordUtils.encryptPassword(String rawPassword, Long roomId, String masterKey)`

| 测试场景 | 输入参数 (对象) | 预期返回值 / 异常 (对象) |
| --- | --- | --- |
| 正常密码加密 | `String("MyP@ssw0rd!"), Long(1), String("test-key")` | `String`（非空，AES-256 密文，以 "AES/GCM/NoPadding" 加密） |
| 同一密码多次加密结果不同（随机 IV） | 同上调用两次 | 两次返回的密文不同（IV 不同），但解密后相同 |
| 同一密码+同一IV可重复解密 | `encrypt` → `decrypt` → 原密码相等 | `Boolean(true)` |

---

### 目标方法：`PasswordUtils.decryptPassword(String encryptedPassword, Long roomId, String masterKey)`

| 测试场景 | 输入参数 (对象) | 预期返回值 / 异常 (对象) |
| --- | --- | --- |
| 正常解密 | `String(encrypt("TestPass123")), Long(1), String("key")` | `String("TestPass123")` |
| 空字符串密码加密后再解密 | `encrypt("")` → `decrypt` | `String("")` |
| 超长密码（200 字符）可正常加解密 | `encrypt("A".repeat(200))` → `decrypt` | `String("A".repeat(200))` |

---

### 目标方法：`PasswordUtils.verifyTotp(String code, String secret)`

| 测试场景 | 输入参数 (对象) | 预期返回值 / 异常 (对象) |
| --- | --- | --- |
| 正确 TOTP 码验证通过 | `String(getCurrentTotp(secret))`, `String(secret)` | `Boolean(true)` |
| 错误 TOTP 码验证失败 | `String("000000")`, `String(secret)` | `Boolean(false)` |
| null 码验证失败 | `null`, `String(secret)` | `Boolean(false)` |
| 空码验证失败 | `String("")`, `String(secret)` | `Boolean(false)` |

---

### 目标方法：`PasswordUtils.getCurrentTotp(String secret)`

| 测试场景 | 输入参数 (对象) | 预期返回值 / 异常 (对象) |
| --- | --- | --- |
| 正常生成 TOTP 码 | `String(base32Secret)` | `String`（6 位数字） |
| 生成连续两次码值不同（时间窗口变化前） | 同上调用两次（极短时间内） | 可能相同或不同（依赖时间步长） |
| null secret 返回 null | `null` | `null` |
| 空 secret 返回 null | `String("")` | `null` |

---

## 第四部分：附录

### 测试覆盖率汇总

| 类别 | 覆盖率说明 |
| --- | --- |
| Controller 接口 (17个) | **14 个前端暴露接口已 100% 在 `RoomApiContractTest` 中覆盖 HTTP 层**；3 个内部接口 (`checkMember`/`getRoomStatus`/`addMembersBatch`) 已在 HTTP 层覆盖 |
| Service 层业务逻辑 | `RoomServiceIntegrationTest` 覆盖：创建/入房/查询/离开/关闭/改密/踢人/续期/重新开启/异步加入/状态变更，共 7 个测试流 |
| 密码加密工具 | `PasswordUtilsTest` 覆盖：AES-256 加密解密、TOTP 生成与验证，共 17 个测试方法 |
| 全链路业务流 | 创建房间→按码查询→申请入房→离开；创建密码房间→错误密码入房被拒→正确密码入房→房主踢人；创建→关闭→续期→重新开启 已覆盖 |
| 边界场景 | 参数校验（null/空/超长/非法值）、权限校验（非房主操作）、ID 不存在、房间码不存在 已覆盖 |
| 暂未覆盖 | DISABLED 状态（STATUS=4）的手动状态变更拦截逻辑；TOTP 模式的动态令牌入房（仅验证 joinRoomAsync 返回 token，未在 Service 层完整验证 MQ 消费链路） |

### 测试运行方式

```bash
cd apps/backend/gopair-room-service
mvn test
```

**依赖环境**：
- MySQL `gopair_test` 数据库（`application-test.yml` 配置）
- Redis（`localhost:6379`，测试使用 database=14）
- MySQL 和 Redis 须在测试前启动

**注意事项**：
- 所有 RoomApiContractTest 中的 HTTP 测试使用真实 TestRestTemplate 调用
- RoomServiceIntegrationTest 直接注入 Service/Mapper，不走 HTTP 层
- `RoomServiceApplicationTests` 保持独立，不继承 BaseIntegrationTest（避免重复 MockBean）
