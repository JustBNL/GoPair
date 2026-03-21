# GoPair Monorepo

GoPair 是一个围绕实时房间协作与沟通打造的多模块项目。后端采用 Spring Boot + Spring Cloud Alibaba 微服务体系，前端基于 Vue 3 + Vite，配套提供压测脚本、Nacos 配置与数据库脚本，帮助快速搭建一套完整的业务闭环。

## 仓库结构

```
.
├── apps
│   ├── backend                  # Maven 多模块后端
│   │   ├── gopair-common        # 通用实体/响应/工具/异常
│   │   ├── gopair-framework-web # 日志、上下文、AOP、鉴权基座
│   │   ├── gopair-gateway       # Spring Cloud Gateway 网关
│   │   ├── gopair-user-service  # 用户 & 认证服务
│   │   ├── gopair-room-service  # 房间、排队、Redis 预占、MQ 事件
│   │   ├── gopair-message-service # 文本消息服务
│   │   └── gopair-websocket-service # WebSocket 长连接
│   └── frontend                 # Vue 3 + Vite 前端应用
├── nacos-configs                # 导入 Nacos 的共享配置
├── perf                         # JMeter / K6 压测脚本与工具
├── sql                          # 数据库初始化脚本
├── *.md                         # 缓存与架构分析文档
└── README.md
```

## 核心能力

- **API Gateway**：单一入口、鉴权、路由、全局 CORS，配置来自 Nacos。
- **User Service**：注册/登录、JWT 签发、用户信息管理。
- **Room Service**：房间创建、异步排队、Redis 预占、定时清理、RabbitMQ 事件通知。
- **Message Service**：房间消息写入与历史查询，结合 Redis channel 推送。
- **WebSocket Service**：统一 WebSocket/SockJS 通道，面向前端推送实时事件。
- **Shared Modules**：`gopair-common` 与 `gopair-framework-web` 负责响应体、上下文、日志、异常及安全拦截器。
- **Frontend**：Vue 3 + Ant Design Vue 管理界面，使用 Pinia / Vue Router / SockJS 实现实时互动。
- **Performance Tooling**：`perf/` 目录包含测试用户引导脚本、JMeter 场景与 K6 脚本，支持 CI 或独立压测。

## 技术栈

| 层级 | 主要技术 |
| ---- | -------- |
| 后端 | Spring Boot 3.2.5 · Spring Cloud 2023.0.0 · Spring Cloud Alibaba 2023.0.1.0 · MyBatis Plus · RabbitMQ · Redis · MySQL · Knife4j · Micrometer |
| 前端 | Vue 3 · Vite 7 · TypeScript · Pinia · Vue Router · Ant Design Vue · Vitest |
| 基建 | Nacos · Redis 6+ · MySQL 8+ · RabbitMQ 3.x · JDK 23 · Maven 3.9+ · pnpm 9+ |

## 快速开始

### 1. 准备基础设施

1. 启动 MySQL、Redis、RabbitMQ，并准备连接账号/密码。
2. 启动 Nacos Server（单机或集群），确认 `DEFAULT_GROUP` 与 namespace。
3. 如需更高吞吐，可搭配 RocketMQ / RocketMQ-Proxy。

### 2. 导入 Nacos 配置

将 `nacos-configs/` 下的 YAML 导入 Nacos：

- `gopair-business.yml`：数据库、Redis、RabbitMQ、MyBatis Plus、Knife4j、AOP 日志等公共配置。
- `gopair-gateway.yml`：网关路由、白名单、鉴权、限流等配置。
- `gopair-logging-base.yml`：日志格式、链路追踪、Actuator 相关配置。

> 以上参数均可通过环境变量覆盖，例如 `NACOS_ADDR`、`DB_HOST`、`JWT_SECRET` 等。

### 3. 构建与启动后端

```bash
# 在仓库根目录构建所有后端模块
mvn -pl apps/backend -am clean package -DskipTests

# 按需启动微服务示例
mvn -pl apps/backend/gopair-gateway spring-boot:run
mvn -pl apps/backend/gopair-user-service spring-boot:run
mvn -pl apps/backend/gopair-room-service spring-boot:run
mvn -pl apps/backend/gopair-message-service spring-boot:run
mvn -pl apps/backend/gopair-websocket-service spring-boot:run
```

> 服务会优先读取 Nacos 中的端口配置；若未设置，则 fallback 到 `application.yml` 的 `SERVER_PORT`（或 0 表示随机端口）。网关默认暴露 `/user`, `/room`, `/message`, `/api/ws` 等路由。

### 4. 启动前端

```bash
cd apps/frontend
pnpm install
pnpm dev --host
```

默认开发地址为 `http://localhost:5173`，可通过 `.env` 设置后端网关地址（如 `VITE_API_BASE`）。

### 5. 常见入口

- Knife4j 文档示例：`http://<user-service-host>:<port>/doc.html`
- 网关健康检查：`http://<gateway-host>:<port>/actuator/health`

## 性能与压测

`perf/` 目录提供两种方案：

1. **JMeter（推荐）**：`perf/jmeter/README.md` 记录 GUI/CLI 操作流程，并提供 `run-*.bat` 脚本快速产出 HTML 报告。
2. **K6**：`perf/k6/join_async_test.js` 支持通过 `BASE_URL`、`ROOM_CODE`、`VUS`、`DURATION` 等环境变量调参，适合脚本化压测。

压测前可运行 `perf/scripts/bootstrap_users_and_tokens.js` 批量创建测试用户并生成 JWT Token（存于 `perf/tokens.txt`），同时预先创建测试房间并记录房间码。

## 常见开发动作

- **数据库初始化**：参照 `sql/` 目录导入建表及样例数据。
- **本地多服务联调**：使用 IntelliJ IDEA / VS Code 多配置启动，或编排 Docker Compose（如有）。
- **新增微服务**：
  1. 在 `apps/backend` 下创建模块并在 `apps/backend/pom.xml` 声明。
  2. 依赖 `gopair-common`、`gopair-framework-web` 复用通用能力。
  3. 在 `gopair-gateway` 中追加路由与过滤配置。
  4. 为新服务创建 Nacos 配置并注入到对应 namespace。
- **日志与排查**：`gopair-framework-web` 内置 AOP 日志、用户上下文，可通过 `logs/` 目录或调整 Nacos 配置追踪关键链路。

## 参考文档

- `CACHE_AVALANCHE_IMPLEMENTATION.md`：缓存雪崩防护策略。
- `ROOM_CACHE_PROTECTION_ANALYSIS.md`：房间缓存一致性与保护机制分析。
- `REDISSON_VS_SPRING_REDIS.md`、`REDIS_CACHE_ANALYSIS.md`：缓存客户端选型与案例。

## 许可证

本项目采用 MIT License，欢迎提交 Issue / PR 共建。若反馈问题，请附上运行环境、已启动的服务、关键日志及复现步骤，便于快速定位。