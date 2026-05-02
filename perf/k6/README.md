# K6 压测方案

基于 K6 的高性能压测方案，验证 GoPair 房间服务与消息服务的核心能力。三个场景覆盖三个独立维度：**并发承受量**、**幂等防超卖**、**消息可靠性**。

## 前置准备

### 1. 安装 K6

**Windows:**

```powershell
# 下载并解压
winget install k6
# 或者手动下载：https://github.com/grafana/k6/releases
```

**macOS:**

```bash
brew install k6
```

**Linux:**

```bash
sudo gpg -k
sudo gpg --no-default-keyring --keyring /tmp/k6-keyring.gpg --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
sudo gpg --no-default-keyring --keyring /tmp/k6-keyring.gpg --export --armor C5AD17C747E3415A3642D57D77C6C491D6AC1D69 | sudo tee /etc/apt/trusted.gpg.d/k6.asc
echo "deb https://dl.k6.io/deb stable main" | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt update
sudo apt install k6
```

### 2. 生成测试用户

```powershell
# 使用 bootstrap 脚本幂等创建用户（自动跳过已存在的）
powershell -ExecutionPolicy Bypass -File perf/run_bootstrap.ps1
```

这会在 `perf/user_info.txt` 中生成 200 条用户数据（token,userId,nickname,email,password），格式为每行：
```
token,userId,nickname,email,password
```
所有场景脚本均从该文件读取凭证，无需手动管理。

### 3. 确保服务启动

```bash
# 启动后端服务（room-service 需要在 localhost:8081 或配置 BASE_URL）
# 确认 Redis、MySQL、Nacos 等依赖服务正常运行
```

---

## 场景说明

### 场景1：容量验证（防超卖脉冲测试）

**维度**：幂等防超卖

**目标**：验证高并发下防超卖能力。当 M >> N 时，最终 JOINED 人数严格等于房间容量。setup 阶段自动创建房间，无需手动准备。

```bash
k6 run -e BASE_URL=http://localhost:8081 `
       -e ROOM_CODE=54273898 `
       -e CAPACITY=10 `
       -e VUS=100 `
       scenarios/capacity.js
```

**参数说明：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| BASE_URL | http://localhost:8081 | 服务地址 |
| ROOM_CODE | 54273898 | 房间码（setup 自动创建，指定后复用已有房间）|
| CAPACITY | 10 | 房间容量 |
| VUS | 100 | 虚拟用户数（应远大于 CAPACITY）|

**预期结果：**
- 控制台输出中 `status=JOINED` 的数量 == CAPACITY
- 若超卖（JOINED > CAPACITY），k6 退出码为非 0

---

### 场景2：登录脉冲摸上限

**维度**：并发承受量

**目标**：在 M 个并发 VU 同时发起登录时，测量系统能承受的最大并发登录数，验证登录接口在脉冲冲击下的 RT 表现。

凭证来源：读取 `user_info.txt` 中的 email/password，自动取文件行数作为 VU 总数，无需手动指定。

```bash
k6 run -e BASE_URL=http://localhost:8081 scenarios/auth_pulse.js
```

**参数说明：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| BASE_URL | http://localhost:8081 | 服务地址 |
| RT_THRESHOLD_P95_MS | 500 | P95 响应时间阈值（ms） |
| RT_THRESHOLD_P99_MS | 1000 | P99 响应时间阈值（ms） |

**阈值配置：**
- 登录 P95 响应时间 < 500ms
- 登录 P99 响应时间 < 1000ms
- HTTP 错误率 < 5%

---

### 场景3：消息发送可靠性验证

**维度**：消息可靠性

**目标**：验证消息服务在高并发下的零丢失、零乱序、零重复可靠性。teardown 阶段通过 VU 计数与数据库实际入库数精确比对判定。

包含三个子模式，通过 MODE 环境变量切换：

| 子模式 | 用途 | VUS | 间隔 |
|--------|------|-----|------|
| `high_freq` | 单用户高频摸发送上限 | 5 | 100ms |
| `group_chat` | 群聊并发模拟真实场景 | 50 | 300ms |
| `reliability` | 零丢失/乱序/重复可靠性验证 | 20 | 200ms |

**场景 A：单用户高频（摸上限）**

```bash
k6 run -e MODE=high_freq -e VUS=5 scenarios/message_basic.js
```

**场景 B：群聊并发（模拟真实）**

```bash
k6 run -e MODE=group_chat -e VUS=50 scenarios/message_basic.js
```

**场景 C：可靠性验证（零丢失）**

```bash
k6 run -e MODE=reliability -e VUS=20 -e DURATION=30s scenarios/message_basic.js
```

**参数说明：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| BASE_URL | http://localhost:8081 | Room Service 地址 |
| MESSAGE_SERVICE_URL | http://localhost:8082 | Message Service 地址 |
| ROOM_CODE | 54273898 | 房间码 |
| ROOM_ID | 自动创建 | 共享房间 ID，指定后跳过创建 |
| VUS | 50 | 虚拟用户数 |
| DURATION | 60s | 压测持续时间 |
| MODE | group_chat | 场景模式 |
| MSG_INTERVAL_MS | 300 | 发送间隔（ms），high_freq/reliability 模式可覆盖 |

**预期结果：**
- `handleSummary` 输出 TPS、RT（P50/P95/P99）、错误率
- `teardown` 阶段对比 MySQL 消息总数与发送次数，验证零丢失

---

## 常用命令

```bash
# ========== 防超卖验证 ==========

# 快速验证防超卖（10 容量，100 并发）
k6 run -e CAPACITY=10 -e VUS=100 scenarios/capacity.js

# ========== 登录脉冲 ==========

# 登录脉冲摸上限（并发数等于 user_info.txt 行数）
k6 run -e BASE_URL=http://localhost:8081 scenarios/auth_pulse.js

# ========== 消息可靠性 ==========

# 消息群聊压测（50 VU，模拟真实群聊）
k6 run -e MODE=group_chat -e VUS=50 scenarios/message_basic.js

# 消息可靠性验证（20 VU，零丢失检查）
k6 run -e MODE=reliability -e VUS=20 -e DURATION=30s scenarios/message_basic.js

# 单用户高频压测（摸发送上限）
k6 run -e MODE=high_freq -e VUS=5 scenarios/message_basic.js

# ========== 辅助 ==========

# 查看详细输出
k6 run --verbose -e MODE=group_chat scenarios/message_basic.js

# 导出 JSON 格式结果
k6 run --out json=results.json scenarios/message_basic.js

# 指定运行日志输出文件
k6 run --log-output=results.log scenarios/message_basic.js
```

---

## 目录结构

```
perf/
├── k6/
│   ├── common.js              ← 共享配置与辅助函数（userData SharedArray）
│   ├── bootstrap.js           ← 引导脚本：幂等注册+登录，写入 user_info.txt
│   ├── scenarios/
│   │   ├── capacity.js        ← 场景1：容量验证（防超卖）
│   │   ├── auth_pulse.js     ← 场景2：登录脉冲摸上限
│   │   └── message_basic.js  ← 场景3：消息发送可靠性验证（含 high_freq/group_chat/reliability 三模式）
│   ├── scripts/
│   │   └── analyze.js        ← 压测后日志分析工具
│   └── README.md              ← 本文件
├── run_bootstrap.ps1          ← bootstrap 执行脚本
└── user_info.txt             ← 测试用户数据（自动生成）
```
