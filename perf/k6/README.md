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

**目标**：验证高并发下防超卖能力。当 M >> N 时，最终 JOINED 人数严格等于房间容量。

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
| ROOM_CODE | 54273898 | 房间码 |
| CAPACITY | 10 | 房间容量 |
| VUS | 100 | 虚拟用户数（应远大于 CAPACITY）|

**预期结果：**
- 控制台输出中 `status=JOINED` 的数量 == CAPACITY
- 若超卖（JOINED > CAPACITY），k6 退出码为非 0

---

### 场景2：持续吞吐（循环进出）

**目标**：测量 RT（P50/P99）、TPS 及系统稳定性。每个 VU 持续执行 `join → poll → 停留 → leave → 等待 → 循环`。

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

### 场景3：容量边界（熔断降级测试）

**目标**：观察容量满后的降级行为，验证 FULL 响应率合理性。

```bash
k6 run -e BASE_URL=http://localhost:8081 \
       -e ROOM_CODE=54273898 \
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
# 快速验证防超卖（10 容量，100 并发）
k6 run -e CAPACITY=10 -e VUS=100 scenarios/capacity.js

# 持续吞吐测试（50 VU，2 分钟）
k6 run -e VUS=50 -e DURATION=120s scenarios/throughput.js

# 查看详细输出
k6 run --verbose -e CAPACITY=10 scenarios/capacity.js

# 导出 JSON 格式结果
k6 run --out json=results.json scenarios/capacity.js

# 指定运行日志输出文件
k6 run --log-output=results.log scenarios/throughput.js
```

---

## 目录结构

```
perf/
├── k6/
│   ├── common.js              ← 共享配置与辅助函数
│   ├── scenarios/
│   │   ├── capacity.js        ← 场景1：容量验证（防超卖）
│   │   ├── throughput.js      ← 场景2：持续吞吐
│   │   └── boundary.js        ← 场景3：容量边界
│   ├── docker-compose.yml     ← InfluxDB + Grafana
│   └── README.md              ← 本文件
├── user_info.txt              ← 测试用户数据（自动生成）
└── scripts/
    └── bootstrap_users_and_tokens.js
```
