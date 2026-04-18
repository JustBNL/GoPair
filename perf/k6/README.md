# K6 压测方案

基于 K6 的高性能压测方案，用于验证 GoPair 房间服务的性能指标和防超卖能力。

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

```bash
node perf/scripts/bootstrap_users_and_tokens.js --count 200
```

这会在 `perf/user_info.txt` 中生成 200 条用户数据（token,userId,nickname），供 K6 CSV Data Set 使用。

### 3. 确保服务启动

```bash
# 启动后端服务（room-service 需要在 localhost:8081 或配置 BASE_URL）
# 确认 Redis、MySQL、Nacos 等依赖服务正常运行
```

---

## 场景说明

### 场景1：容量验证（防超卖脉冲测试）⚡ 核心场景

**目标**：验证高并发下防超卖能力。当 M >> N 时，最终 JOINED 人数严格等于房间容量。setup 阶段自动创建房间，无需手动准备。

```bash
k6 run -e BASE_URL=http://localhost:8081 \
       -e ROOM_CODE=54273898 \
       -e CAPACITY=10 \
       -e VUS=100 \
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

### 场景2：持续吞吐（循环进出）

**目标**：测量 RT（P50/P99）、TPS 及系统稳定性。每个 VU 持续执行 `join → poll → 停留 → leave → 等待 → 循环`。setup 阶段自动创建房间。

```bash
k6 run -e BASE_URL=http://localhost:8081 \
       -e ROOM_CODE=54273898 \
       -e VUS=50 \
       -e DURATION=60s \
       scenarios/throughput.js
```

**参数说明：**
| 参数 | 默认值 | 说明 |
|------|--------|------|
| DURATION | 60s | 压测持续时间 |
| MIN_STAY_MS | 2000 | 最小停留时间(ms) |
| MAX_STAY_MS | 5000 | 最大停留时间(ms) |

**阈值配置：**
- `join/async` P99 响应时间 < 500ms
- `poll/result` P99 响应时间 < 200ms
- HTTP 错误率 < 5%

---

### 场景3：消息发送高频压测

**目标**：验证消息服务在高并发下的吞吐量、RT 表现及零丢失可靠性。三个子场景满足不同测试需求。

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

### 场景4：容量边界（熔断降级测试）

**目标**：观察容量满后的降级行为，验证 FULL 响应率合理性。setup 阶段自动创建房间。

```bash
k6 run -e BASE_URL=http://localhost:8081 \
       -e ROOM_CODE=54540797 \
       -e CAPACITY=10 \
       -e VUS=30 \
       -e DURATION=60s \
       scenarios/boundary.js
```

**预期结果：**
- JOINED 达到容量后，新请求陆续收到 FAILED 响应
- 已加入的用户能正常离开
- FULL/FAILED 响应率合理（不超过总请求的合理比例）

---

### 场景6：登录脉冲摸上限

**目标**：在 M 个并发 VU 同时发起登录时，测量系统能承受的最大并发登录数，验证登录接口在脉冲冲击下的 RT 表现。

setup 阶段自动注册测试用户（`@example.com` 域跳过验证码），无需手动准备凭证。

```bash
k6 run -e BASE_URL=http://localhost:8081 \
       -e AUTH_USER_COUNT=200 \
       auth/auth_pulse.js
```

**参数说明：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| BASE_URL | http://localhost:8081 | 服务地址 |
| AUTH_USER_COUNT | 200 | 虚拟用户数（等于注册用户数） |
| RT_THRESHOLD_P95_MS | 500 | P95 响应时间阈值（ms） |
| RT_THRESHOLD_P99_MS | 1000 | P99 响应时间阈值（ms） |

**阈值配置：**
- 登录 P95 响应时间 < 500ms
- 登录 P99 响应时间 < 1000ms
- HTTP 错误率 < 5%

---

### 场景7：持续登录吞吐

**目标**：在长时间高负载下，测量登录接口的持续 TPS 与 RT 稳定性，验证系统在持续登录压力下的表现。

每个 VU 持续执行：登录 → 停留 → 等待 → 重新登录。

```bash
k6 run -e BASE_URL=http://localhost:8081 \
       -e AUTH_USER_COUNT=200 \
       -e VUS=100 \
       -e DURATION=60s \
       auth/auth_sustained.js
```

**参数说明：**

| 参数 | 默认值 | 说明 |
|------|--------|------|
| BASE_URL | http://localhost:8081 | 服务地址 |
| AUTH_USER_COUNT | 200 | 凭证总数 |
| VUS | 50 | 虚拟用户数 |
| DURATION | 60s | 压测持续时间 |
| MIN_STAY_MS | 2000 | 最小停留时间（ms） |
| MAX_STAY_MS | 5000 | 最大停留时间（ms） |
| MIN_WAIT_MS | 1000 | 重新登录前等待（ms） |
| MAX_WAIT_MS | 2000 | 重新登录前等待（ms） |

---

### 场景8：WebSocket Fan-out 压测（WS 分发层）

**目标**：测量 ChannelMessageRouter 在 N 人房间下的消息分发能力，验证 fan-out 延迟和分发成功率。

**前置条件**：
- Node.js + `ws` + `amqplib` 已安装
- RabbitMQ / WebSocket 服务正常运行
- 测试用户数据已生成
- **必须指定一个已存在的房间 ID**（可通过其他场景的 setup 阶段创建，或手动创建）

**安装依赖**：
```bash
cd perf/k6/scripts
npm install ws amqplib
```

**运行步骤**：
```bash
# 1. 先用 message_basic.js 建立房间（setup 阶段自动创建）
k6 run -e ROOM_CODE=88888888 scenarios/message_basic.js
# 观察输出中的 [setup] 创建房间成功 roomId=XX

# 2. 将房间 ID 传入 WS Fan-out 压测
k6 run -e ROOM_ID=XX scenarios/message_ws_fanout.js

# 3. 运行 Node.js WS Fan-out 客户端（获取精确延迟）
node perf/k6/scripts/ws_fanout_client.js \
    --ws-url=ws://localhost:8080/ws \
    --room-id=XX \
    --subscribers=50 \
    --inject=100
```

**参数说明**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| ws-url | ws://localhost:8080/ws | WebSocket 服务地址 |
| room-id | 18 | 房间 ID（必须为已存在的房间）|
| subscribers | 50 | 订阅者数量 |
| inject | 100 | 注入消息数量 |
| mq-url | amqp://guest:guest@localhost:5672 | RabbitMQ 地址 |

**预期结果**：
- Fan-out P99 延迟 < 500ms
- 消息丢失率 < 1%
- 分发成功率 > 99%

**测量指标**：
- P50/P95/P99 fan-out 延迟
- 分发吞吐量（消息/秒）
- 订阅者丢消息比例

---

## InfluxDB + Grafana 可视化（答辩展示用）

### 启动监控栈

```bash
cd perf/k6
docker compose up -d
```

等待 InfluxDB 启动完成（约 10s），然后运行压测：

```bash
k6 run --out influxdb=http://localhost:8086 \
       -e BASE_URL=http://localhost:8081 \
       -e ROOM_CODE=54273898 \
       -e CAPACITY=10 \
       -e VUS=100 \
       scenarios/capacity.js
```

### 访问 Grafana

打开 http://localhost:3000 ，使用 `admin / adminpassword` 登录。

导入 K6 官方仪表盘：
1. 点击左侧 "+" → "Import"
2. 输入仪表盘 ID：`2587`（K6 官方仪表盘）
3. 选择 InfluxDB 数据源（URL: `http://influxdb:8086`，Token: `my-super-secret-token`）
4. 点击 "Import"

### 停止监控栈

```bash
docker compose down
```

---

## 常用命令

```bash
# ========== 房间服务压测 ==========

# 快速验证防超卖（10 容量，100 并发）
k6 run -e CAPACITY=10 -e VUS=100 scenarios/capacity.js

# 持续吞吐测试（50 VU，2 分钟）
k6 run -e VUS=50 -e DURATION=120s scenarios/throughput.js

# ========== 消息服务压测 ==========

# 消息群聊压测（50 VU，模拟真实群聊）
k6 run -e MODE=group_chat -e VUS=50 scenarios/message_basic.js

# 消息可靠性验证（20 VU，零丢失检查）
k6 run -e MODE=reliability -e VUS=20 -e DURATION=30s scenarios/message_basic.js

# 单用户高频压测（摸发送上限）
k6 run -e MODE=high_freq -e VUS=5 scenarios/message_basic.js

# 消息发送 + InfluxDB 输出（供 Grafana 展示）
k6 run --out influxdb=http://localhost:8086 \
       -e MODE=group_chat -e VUS=50 \
       scenarios/message_basic.js

# ========== 登录服务压测 ==========

# 登录脉冲摸上限（200 并发）
k6 run -e BASE_URL=http://localhost:8081 \
       -e AUTH_USER_COUNT=200 \
       auth/auth_pulse.js

# 登录脉冲 + InfluxDB 输出
k6 run --out influxdb=http://localhost:8086 \
       -e AUTH_USER_COUNT=200 \
       auth/auth_pulse.js

# 持续登录吞吐（100 VU，60s）
k6 run -e BASE_URL=http://localhost:8081 \
       -e AUTH_USER_COUNT=200 \
       -e VUS=100 \
       -e DURATION=60s \
       auth/auth_sustained.js

# ========== WS Fan-out 压测 ==========

# WS 分发层压测（50 人房间，100 条消息）
node perf/k6/scripts/ws_fanout_client.js \
    --ws-url=ws://localhost:8080/ws \
    --room-id=18 \
    --subscribers=50 \
    --inject=100

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
│   ├── common.js              ← 共享配置与辅助函数（含 sendMessage/countRoomMessages/createTestRoom）
│   ├── auth/
│   │   ├── common_auth.js     ← auth 共享凭证模块（setup 注册 + credentials SharedArray）
│   │   ├── auth_pulse.js     ← 场景6：登录脉冲摸上限
│   │   └── auth_sustained.js ← 场景7：持续登录吞吐
│   ├── scenarios/
│   │   ├── capacity.js        ← 场景1：容量验证（防超卖）
│   │   ├── throughput.js      ← 场景2：持续吞吐
│   │   ├── message_basic.js  ← 场景3：消息发送压测（含可靠性验证）
│   │   ├── message_ws_fanout.js ← 场景8：WS Fan-out 压测
│   │   └── boundary.js       ← 场景4：容量边界
│   ├── scripts/
│   │   ├── ws_fanout_client.js ← WS Fan-out 独立测量客户端
│   │   ├── bootstrap_users_and_tokens.js
│   │   └── analyze.js
│   ├── grafana-provisioning/ ← Grafana 自动配置
│   │   ├── datasources/
│   │   └── dashboards/
│   ├── docker-compose.yml     ← InfluxDB + Grafana
│   └── README.md              ← 本文件
├── user_info.txt              ← 测试用户数据（自动生成）
└── scripts/
```
