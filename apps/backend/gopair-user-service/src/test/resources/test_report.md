# gopair-user-service 测试进度与用例汇总

> 服务名称：gopair-user-service
> 基准路径：`apps/backend/gopair-user-service`
> 生成时间：2026-05-02

---

## 第一部分：测试代码结构布局 (Tree Diagram)

```
src/test/java/com/gopair/userservice/
  ├─ base/
  │    ├─ BaseIntegrationTest.java     (集成测试抽象基类，@Transactional + Redis flushDb())
  │    └─ TestMailConfig.java          (@TestConfiguration，JavaMailSender 桩)
  ├─ service/
  │    └─ impl/
  │         └─ PasswordUtilsUnitTest.java  (无 Spring 容器，纯单元测试)
  ├─ api/
  │    └─ UserApiContractTest.java     (API 契约测试，唯一集成测试入口，@Nested 分组)
  ├─ service/
  │    └─ StubEmailServiceImpl.java   (EmailService 测试替身，@Primary)
  └─ UserServiceApplicationTests.java   (上下文加载测试)
```

### 继承结构

```
BaseIntegrationTest (abstract, @SpringBootTest, @Transactional)
  └── UserApiContractTest

PasswordUtilsUnitTest             (standalone, @ExtendWith(MockitoExtension)，无 Spring)
UserServiceApplicationTests       (@SpringBootTest, 无继承)
```

### 脏数据清理机制

- **MySQL**：`BaseIntegrationTest` 的 `@Transactional` 注解确保每个测试方法结束后自动回滚
- **Redis**：`BaseIntegrationTest` 的 `@AfterEach` 执行 `flushDb()` 清空当前 DB（database=15）
- **分页测试**：`PageUserTests` 使用 `@Rollback(false)` 保持数据积累，以验证分页逻辑

---

## 第二部分：接口集成测试用例 (Integration Tests)

### 接口：POST /user/sendCode (发送验证码)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 发送成功（@example.com 绕过验证） | `{"email":"sendcode_xxx@example.com","type":"register"}` | `{"code":200}` | 会插入一条 Redis 键值为 `verify:code:register:sendcode_xxx@example.com` = 6位数字 的数据（TTL 过期） |
| 忘记密码场景 - 邮箱未注册 | `{"email":"notreg_xxx@example.com","type":"resetPassword"}` | `{"code":20101,"msg":"邮箱不存在"}` | 会抛出 `UserException`，事务不产生副作用 |
| 暂未编写测试（边界：type 参数为空/null/非法值） | - | - | - |

---

### 接口：POST /user/forgotPassword (忘记密码/验证码重置)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 重置成功 | `{"email":"forgot_xxx@example.com","code":"<real_code>","newPassword":"NewP@ss123"}` | `{"code":200}` | `user` 表中该用户的 `password` 字段被更新为新密码的 BCrypt 哈希；Redis 键 `verify:code:resetPassword:forgot_xxx@example.com` 被删除 |
| 验证码错误 - 重置失败 | `{"email":"wrongcode_xxx@example.com","code":"000000","newPassword":"NewP@ss123"}` | `{"code":20106,"msg":"验证码错误"}` | 会抛出 `UserException`，Redis 中验证码不会被消费（不删除），触发事务回滚 |
| 邮箱未注册（先校验验证码，Redis无此邮箱记录则报验证码无效） | `{"email":"ghost_xxx@example.com","code":"123456","newPassword":"NewP@ss123"}` | `{"code":20106,"msg":"验证码错误"}` | 会抛出 `UserException`，触发事务回滚 |
| 空值参数 - 重置失败 | `{"email":"","code":"123456","newPassword":"NewP@ss123"}` 或其他字段为空 | `{"code":20106}` | 会抛出 `UserException`，触发事务回滚 |
| 暂未编写测试（边界：邮箱格式非法） | - | - | - |

---

### 接口：POST /user/login (用户登录)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 正常登录 | `{"email":"loginuser_xxx@example.com","password":"P@ss1234"}` | `{"code":200,"data":{"userId":1,"nickname":"loginuser_xxx","token":"eyJ..."}}` | `user` 表按 email + password 查询，无写入副作用 |
| 用户不存在 | `{"email":"notexist_xxx@example.com","password":"P@ss1234"}` | `{"code":20100,"msg":"用户不存在"}` | 无数据库/缓存副作用 |
| 密码错误 | `{"email":"wrongpwd_xxx@example.com","password":"WrongPassword1"}` | `{"code":20102,"msg":"密码错误"}` | 无数据库/缓存副作用 |
| 空或null参数 - 登录失败 | `{"email":"","password":"password"}` / `{"email":null,"password":"password"}` 等 | `{"code":20106,"msg":"参数缺失"}` | 无数据库/缓存副作用 |

---

### 接口：POST /user/register (用户注册)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功注册（@example.com 跳过验证码） | `{"nickname":"alice_xxx","email":"alice_xxx@example.com","password":"P@ss1234"}` | `{"code":200,"data":{"userId":1,"nickname":"alice_xxx","email":"alice_xxx@example.com","message":"注册成功"}}` | `user` 表会插入一条数据，nickname/email 不重复，status='0'，password 为 BCrypt 哈希 |
| 昵称重复 | `{"nickname":"bob_xxx","email":"bob2_xxx@example.com","password":"P@ss1234"}` | `{"code":20103,"msg":"昵称已被占用"}` | 无数据库写入，触发事务回滚 |
| 邮箱重复 | `{"nickname":"user2_xxx","email":"dup_xxx@example.com","password":"P@ss1234"}` | `{"code":20101,"msg":"邮箱已被占用"}` | 无数据库写入，触发事务回滚 |
| 昵称为空 | `{"nickname":"","email":"test@example.com","password":"P@ss1234"}` | `{"code":20106}` | 无数据库写入 |
| 昵称为 null | `{"nickname":null,"email":"test@example.com","password":"P@ss1234"}` | `{"code":20106}` | 无数据库写入 |
| 密码为空 | `{"nickname":"user","email":"test@example.com","password":""}` | `{"code":20106}` | 无数据库写入 |
| 密码为 null | `{"nickname":"user","email":"test@example.com","password":null}` | `{"code":20106}` | 无数据库写入 |
| 邮箱为空 | `{"nickname":"user","email":"","password":"P@ss1234"}` | `{"code":20106}` | 无数据库写入 |
| 邮箱为 null | `{"nickname":"user","email":null,"password":"P@ss1234"}` | `{"code":20106}` | 无数据库写入 |
| 邮箱格式错误 | `{"nickname":"user","email":"not-an-email","password":"P@ss1234"}` | `{"code":20105,"msg":"邮箱格式错误"}` | 无数据库写入 |
| 昵称过长（21字符） | `{"nickname":"aaaaaaaaaaaaaaaaaaaaa","email":"test@example.com","password":"P@ss1234"}` | `{"code":20104,"msg":"昵称过长"}` | 无数据库写入 |
| 密码过短（5字符） | `{"nickname":"user","email":"test@example.com","password":"12345"}` | `{"code":20106}` | 无数据库写入 |
| 密码过长（51字符） | `{"nickname":"user","email":"test@example.com","password":"<51 chars>"}` | `{"code":20106}` | 无数据库写入 |

---

### 接口：PUT /user (更新用户)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功更新（不含密码修改） | `{"userId":1,"nickname":"updated_xxx","email":"updated_xxx@example.com"}` | `{"code":200,"data":true}` | `user` 表中该用户的 nickname 和 email 字段被更新 |
| 成功更新（含密码修改） | `{"userId":1,"nickname":"nick","email":"a@example.com","password":"NewP@ss456","currentPassword":"OldP@ss123"}` | `{"code":200,"data":true}` | `user` 表中该用户的 nickname/email/password 字段被更新；旧密码验证通过，新密码 BCrypt 哈希写入 |
| 改密成功 - 新密码可登录，旧密码不可用 | `{"userId":1,"password":"NewP@ss456","currentPassword":"OldP@ss123"}` | `{"code":200}` | 新密码匹配成功，旧密码匹配失败 |
| 改密失败 - 当前密码错误 | `{"userId":1,"password":"NewP@ss456","currentPassword":"WrongPassword"}` | `{"code":20102,"msg":"密码错误"}` | 会抛出 `UserException`，password 字段不变，触发事务回滚 |
| 改密失败 - 新旧密码相同 | `{"userId":1,"password":"SameP@ss123","currentPassword":"SameP@ss123"}` | `{"code":20108,"msg":"新密码不能与旧密码相同"}` | 会抛出 `UserException`，触发事务回滚 |
| 用户不存在 | `{"userId":999999,"nickname":"ghost_xxx"}` | `{"code":20100,"msg":"用户不存在"}` | 无数据库写入，触发事务回滚 |
| 昵称冲突 | `{"userId":3,"nickname":"otheruser_xxx"}` | `{"code":20103,"msg":"昵称已被占用"}` | `user` 表无变更，触发事务回滚 |
| 邮箱冲突 | `{"userId":3,"email":"user2_xxx@example.com"}` | `{"code":20101,"msg":"邮箱已被占用"}` | `user` 表无变更，触发事务回滚 |
| 更新自身昵称 - 允许 | `{"userId":1,"nickname":"selfnick_xxx"}`（昵称与当前相同） | `{"code":200}` | `user` 表该用户 nickname 不变，无异常 |
| 更新自身邮箱 - 允许 | `{"userId":1,"email":"selfmail_xxx@example.com"}`（邮箱与当前相同） | `{"code":200}` | `user` 表该用户 email 不变，无异常 |
| 空值参数 - 更新失败 | `{"userId":null,"nickname":"nick","email":"e@e.com"}` 等 | `{"code":20106}` | 会抛出 `UserException`，触发事务回滚 |

---

### 接口：DELETE /user/{userId} (删除用户)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 正常删除 | `DELETE /user/1` | `{"code":200,"data":true}` | `user` 表中该记录被物理删除；删除后 GET /user/1 返回 20100 错误 |
| 用户不存在 | `DELETE /user/999999` | `{"code":20100,"msg":"用户不存在"}` | 无数据库变更，触发事务回滚 |

---

### 接口：GET /user/by-ids (批量查询用户)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 批量查询成功 | `GET /user/by-ids?ids=1,2` | `{"code":200,"data":[...2 users]}` | `user` 表按主键 IN 查询，无写入副作用 |
| 空字符串查询 | `GET /user/by-ids?ids=` | `{"code":200,"data":[]}` | 无数据库/缓存副作用 |
| 混合存在与不存在的ID | `GET /user/by-ids?ids=<existingId>,999998,999999` | `{"code":200,"data":[...1 user]}` | 仅返回存在的用户；不存在的 ID 被静默过滤 |
| 重复ID去重 | `GET /user/by-ids?ids=1,1,1` | `{"code":200,"data":[...1 user]}` | 去重逻辑生效，IN 查询去重至 1 个 ID |
| 参数含非法片段自动跳过 | `GET /user/by-ids?ids=<validId>,abc,xyz` | `{"code":200,"data":[...1 user]}` | 非数字片段被跳过，仅解析有效 ID |

---

### 接口：GET /user/{userId} (根据 ID 查询用户)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功查询 | `GET /user/1` | `{"code":200,"data":{"userId":1,"nickname":"getuser_xxx","email":"getuser_xxx@example.com","status":"0"}}` | `user` 表按主键查询，无写入副作用 |
| 用户不存在 | `GET /user/999999` | `{"code":20100,"msg":"用户不存在"}` | 无数据库/缓存副作用 |
| 非法ID - 查询失败 | `GET /user/0` 或 `GET /user/-1` | `{"code":20100}` 或异常响应 | 无数据库/缓存副作用 |

---

### 接口：GET /user/page (分页查询用户)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- | --- |
| 分页成功 | `GET /user/page?pageNum=1&pageSize=10` | `{"code":200,"data":{"total":>=5,"records":[...]}}` | `user` 表按 pageNum/pageSize 分页查询；仅支持 pageNum >= 1 且 pageSize >= 1 |
| 大页码返回空 | `GET /user/page?pageNum=999&pageSize=10` | `{"code":200,"data":{"records":[]}}` | 返回空列表，不报错 |
| 非法页码参数 | `pageNum=0&pageSize=10` / `pageNum=-1&pageSize=10` / `pageNum=1&pageSize=0` | `{"code":200/400/500}` | 行为依赖 PageHelper 配置 |
| 超大pageSize仍正常返回 | `GET /user/page?pageNum=1&pageSize=1000` | `{"code":200}` | 可能正常返回大量数据（无上限保护） |

---

### 接口：DELETE /user/{userId}/cancel (注销账号)

| 测试场景 | 输入参数 (JSON) | 预期输出 (JSON) | 预期数据库/缓存/异常状态 |
| --- | --- | --- | --- |
| 成功注销 | `DELETE /user/1/cancel` | `{"code":200}` | `user` 表中该用户的 status 字段更新为 '2'（CANCELLED）；email 字段追加 `#deleted_` + 时间戳 |
| 用户不存在 - 注销失败 | `DELETE /user/999999/cancel` | `{"code":20100,"msg":"用户不存在"}` | 无数据库变更，触发事务回滚 |
| 重复注销 - 失败 | `DELETE /user/1/cancel`（第二次） | `{"code":20107,"msg":"账号已注销"}` | 会抛出 `UserException`，事务回滚，无变更 |
| 注销后邮箱追加 #deleted_ 前缀 | `DELETE /user/1/cancel` 后查询登录 | 登录失败，message 含"用户不存在" | email 字段被追加 `#deleted_<timestamp>` |
| 注销后原邮箱可重新注册 | 同一邮箱再次调用 POST /user/register | `{"code":200}` | 新注册生成新的 userId，email 恢复为原邮箱 |

---

## 第三部分：单元测试用例汇总 (Unit Tests)

### 目标方法：`PasswordUtils.encode(String rawPassword)`

| 测试场景 | 输入参数 (对象) | 预期返回值 / 异常 (对象) |
| --- | --- | --- |
| 正常密码加密应返回非空且不同于原始密码的哈希值 | `String("MySecureP@ssw0rd!")` | `String`（以 `$2a$` 或 `$2b$` 开头，长度 60） |
| 同一密码多次加密结果应不同（随机盐） | `String("123456")`（同一入参调用两次） | 两次返回的哈希值不同，但长度相等 |
| 空密码应可正常加密 | `String("")` | `String`（非空 BCrypt 哈希） |
| 超长密码应可正常加密 | `String("A".repeat(200))` | `String`（非空 BCrypt 哈希，`matches()` 验证为 true） |

---

### 目标方法：`PasswordUtils.matches(String rawPassword, String encodedPassword)`

| 测试场景 | 输入参数 (对象) | 预期返回值 / 异常 (对象) |
| --- | --- | --- |
| 正确密码匹配应返回 true | `String("CorrectHorseBattery!")` 和其 BCrypt 哈希 | `Boolean(true)` |
| 错误密码匹配应返回 false | `String("WrongPassword")` 和 `String("CorrectPassword")` 的哈希 | `Boolean(false)` |
| 大小写敏感的匹配 | `String("ABCDEF")` vs `String("abcdef")` 的哈希 | `Boolean(false)` |
| 包含特殊字符的密码匹配 | `String("P@ssw0rd!#$%^&*()_+-=[]{}|;':\".,./<>?")` 和其哈希 | `Boolean(true)` |
| 哈希值被篡改后匹配应返回 false | 原密码哈希末位被替换为 "X" | `Boolean(false)` |
| 空字符串密码正确匹配 | `String("")` 和空字符串的哈希 | `Boolean(true)` |
| 空字符串密码错误匹配 | `String("notempty")` 和空字符串的哈希 | `Boolean(false)` |
| 新密码与旧密码不能相同的业务校验（模拟） | 旧密码的 BCrypt 哈希，raw=老密码 | `Boolean(true)`（matches 返回 true 触发业务层拒绝） |

---

## 附录：测试覆盖率汇总

| 类别 | 覆盖率说明 |
| --- | --- |
| Controller 接口 (10个) | `sendCode`、`forgotPassword`、`login`、`register`、`updateUser`、`deleteUser`、`getUserById`、`getUserPage`、`cancelAccount`、`listUsersByIds` 已在 `UserApiContractTest` 中 100% 覆盖 HTTP 层 |
| 密码加密工具 | `PasswordUtils.encode` 和 `matches` 已 100% 覆盖单元测试 |
| 全链路业务流 | 注册→登录→改密→注销→再注册；发送验证码→重置密码→新密码登录 已覆盖 |
| 边界场景 | 参数校验、ID边界（0/-1）、分页边界（0/-1/1000）、混合ID批量查询、非法片段跳过、去重 已覆盖 |
| 暂未覆盖 | `sendCode` 的 type 参数非法值场景；`forgotPassword` 的邮箱格式非法场景；`getUserPage` 的 pageSize=1000 无上限保护 |

---

## 测试运行方式

```bash
cd apps/backend/gopair-user-service
mvn test
```

**依赖环境**：
- MySQL `gopair_test` 数据库（`application-test.yml` 配置）
- Redis（`localhost:6379`，测试使用 database=15）
- MySQL 和 Redis 须在测试前启动
