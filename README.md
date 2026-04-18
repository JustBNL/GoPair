# GoPair Monorepo

GoPair 是一个围绕**实时房间协作与沟通**打造的全栈 Monorepo 项目。后端采用 Spring Boot 3 + Spring Cloud Alibaba 微服务体系，前端基于 Vue 3 + Vite，支持实时聊天、文件共享、WebRTC 语音通话、AI 助手等核心功能，配套 Nacos 配置中心、OpenTelemetry 可观测性（链路追踪 + 结构化日志 → OpenObserve）、K6 压测与数据库初始化脚本，帮助快速搭建一套完整的业务闭环。

---

## 目录

- [仓库结构](#仓库结构)
- [核心功能](#核心功能)
- [技术栈](#技术栈)
- [服务架构](#服务架构)
- [快速开始](#快速开始)
- [Nacos 配置说明](#nacos-配置说明)
- [API 概览](#api-概览)
- [WebSocket 消息协议](#websocket-消息协议)
- [前端结构](#前端结构)
- [性能与压测](#性能与压测)
- [常见开发动作](#常见开发动作)
- [参考文档](#参考文档)
- [许可证](#许可证)

---

## 仓库结构

```
.
├── apps/
│   ├── backend/                           # Maven 多模块后端
│   │   ├── gopair-common/               # 通用响应体 R<T>、异常基类、JWT 工具、Bean 拷贝、MQ 生产者基座
│   │   ├── gopair-framework-web/         # 日志 AOP、用户上下文、MyBatis Plus 自动填充、全局异常处理
│   │   ├── gopair-gateway/              # Spring Cloud Gateway 网关（JWT 鉴权、路由、CORS、全局日志）
│   │   ├── gopair-user-service/         # 用户注册/登录、JWT 签发、邮箱验证码、账号管理
│   │   ├── gopair-room-service/         # 房间创建/加入/离开、异步排队、Redis 预占、RabbitMQ 事件
│   │   ├── gopair-message-service/      # 房间文本消息写入与历史查询
│   │   ├── gopair-file-service/         # MinIO 文件上传/下载/预览，图片缩略图，头像压缩
│   │   ├── gopair-voice-service/        # WebRTC 语音通话管理、服务端信令中转
│   │   ├── gopair-websocket-service/    # 统一 WebSocket 长连接，频道订阅，MQ 消息实时推送
│   │   └── gopair-admin-service/        # 独立管理后台（后端 + admin-frontend），端口 8088
│   └── frontend/                          # 用户端 Vue 3 + Vite 前端应用
├── nacos-configs/                         # 导入 Nacos 的共享配置
│   ├── gopair-shared.yml                # 数据源、Redis、RabbitMQ、MyBatis Plus、Knife4j
│   ├── gopair-gateway.yml               # 网关路由、JWT 白名单、Actuator
│   └── gopair-logging-base.yml          # 日志格式、链路追踪（OTLP）、OpenObserve 配置
├── perf/                                  # K6 压测脚本
├── sql/                                   # 数据库初始化脚本
├── CHANGELOG.md                           # 项目变更日志
├── .env                                   # 本地环境变量（不提交至版本库）
└── README.md
```

---

## 核心功能

| 功能模块 | 说明 |
|---------|------|
| 用户认证 | 邮箱注册（验证码校验）、密码登录、JWT 签发、忘记密码重置、账号注销（软删除） |
| 房间管理 | 创建/加入/离开/关闭房间，支持密码模式与动态令牌两种验证方式，房主踢人 |
| 异步排队入房 | 高并发下通过 RabbitMQ 异步排队 + Redis Lua 脚本原子预占席位，轮询查询入房结果，死信队列兜底 |
| 实时聊天 | WebSocket 频道消息推送，Redis 令牌桶限流（按消息类型差异化消耗），支持 Emoji，消息历史持久化至 MySQL |
| 文件共享 | 房间内文件上传/下载/预览（MinIO），图片自动生成缩略图，头像上传自动压缩为 200×200 |
| 语音通话 | 基于 WebRTC P2P 的房间语音通话，服务端信令中转，支持按需创建/加入/就绪通知/离开/结束 |
| WebSocket 推送 | 统一长连接服务，基于 Token 桶限流，支持频道订阅/取消，RabbitMQ → WebSocket 实时事件下发 |
| AI 助手 | 前端集成 AI Chat 抽屉，支持对话式 AI 辅助 |
| 管理后台 | 管理员面板：用户/房间/消息/文件/通话记录管理，操作审计日志，JWT 独立鉴权 |
| 日志与监控 | `@LogRecord` AOP 注解记录业务操作，Micrometer + Prometheus 指标暴露，链路追踪 MDC 注入，OTLP → OpenObserve 结构化日志 |

---

## 毕设演示功能总览

```
GoPair 实时房间协作平台
│
├── 用户认证
│   ├── 邮箱注册（验证码校验）
│   ├── 密码登录 / JWT 签发
│   ├── 忘记密码（邮箱重置）
│   └── 账号注销（软删除）
│
├── 房间管理
│   ├── 创建房间（密码模式 / 动态令牌模式）
│   ├── 加入 / 离开 / 关闭房间
│   ├── 高并发异步排队入房（RabbitMQ + Redis Lua 原子预占）
│   └── 房主踢人
│
├── 实时聊天
│   ├── WebSocket 实时消息推送
│   ├── 消息限流（Redis 令牌桶，按消息类型差异化消耗）
│   ├── Emoji 表情支持
│   └── 历史消息持久化 + 分页查询
│
├── 文件共享
│   ├── 文件上传 / 下载 / 预览（MinIO）
│   ├── 图片自动生成缩略图
│   └── 头像上传自动压缩（200×200）
│
├── WebRTC 语音通话
│   ├── P2P 语音通话（服务端信令中转）
│   └── 多人同房间通话支持
│
├── AI 助手（房间列表页）
│   └── 对话式 AI Chat，支持流式输出
│
├── 管理后台（独立端口 8088）
│   ├── 仪表盘（全局统计）
│   ├── 用户管理（停用 / 启用）
│   ├── 房间管理（强制关闭）
│   ├── 消息管理（按房间查看）
│   ├── 文件管理（元数据删除）
│   ├── 通话记录（参与者查询）
│   └── 审计日志（操作记录）
│
└── 工程能力
    ├── 链路追踪（Micrometer Tracing + OTLP → OpenObserve）
    ├── 结构化日志（Logback → OTLP → OpenObserve）
    ├── @LogRecord AOP 操作日志
    └── 微服务架构（Gateway / Nacos / RabbitMQ）
```

---

## 技术栈

### 后端

| 层级 | 技术选型 |
|------|----------|
| 框架 | Spring Boot 3.2.5 · Spring Cloud 2023.0.0 · Spring Cloud Alibaba 2023.0.1.0 |
| 网关 | Spring Cloud Gateway（响应式） |
| 服务注册/配置 | Nacos 2.x（服务发现 + 配置中心） |
| ORM | MyBatis Plus · PageHelper |
| 数据库 | MySQL 8+ |
| 缓存 | Redis 6+（Lettuce 连接池，Lua 脚本原子操作） |
| 消息队列 | RabbitMQ 3.x（Topic Exchange、Dead Letter Queue） |
| 对象存储 | MinIO |
| 安全 | JWT · BCrypt 密码加密 |
| API 文档 | Knife4j（OpenAPI 3） |
| 可观测性 | Micrometer Tracing · Brave · OpenTelemetry OTLP → OpenObserve（链路追踪 + 结构化日志） |
| 构建 | Maven 3.9+ · JDK 23 |

### 前端

| 层级 | 技术选型 |
|------|----------|
| 框架 | Vue 3 · TypeScript |
| 构建工具 | Vite 7 |
| UI 组件库 | Ant Design Vue 4 |
| 状态管理 | Pinia 3 |
| 路由 | Vue Router 4 |
| HTTP 客户端 | Axios |
| WebSocket | SockJS-client |
| 实时通话 | WebRTC（自研 WebRTCManager、P2PConnectionManager、AudioDeviceManager、SignalingProtocol） |
| 测试 | Vitest · @vue/test-utils |
| 包管理 | pnpm 9+ |

---

## 服务架构

```
┌──────────────────────────────────────────────────────────────┐
│         用户端前端 (Vue 3 + Vite)  http://localhost:5173     │
│   LoginView  /  RoomsView  /  RoomDetailView                 │
│   Chat · File · Voice · AI 功能组件                           │
└──────────────┬──────────────────────┬───────────────────────┘
               │ HTTP/REST             │ WebSocket (SockJS)
               ▼                      ▼
┌──────────────────────────────────────────────────────────────┐
│        gopair-gateway  (Spring Cloud Gateway)  :8081          │
│   JWT 鉴权过滤器 · 路由 · 全局 CORS · 请求日志               │
│   白名单: /user/login  /user  /user/sendCode  ...          │
└───┬──────┬──────┬────────┬──────┬───────────┬────────┬────┘
    │      │      │        │      │           │        │
    ▼      ▼      ▼        ▼      ▼           ▼        ▼
┌──────┐┌─────┐┌──────┐┌──────┐┌───────┐┌──────────┐┌─────┐
│ user ││room ││ msg  ││ file ││ voice ││websocket ││admin│
│ svc  ││ svc ││ svc  ││ svc  ││  svc  ││  svc     ││ svc │
└──────┘└──┬──┘└──────┘└──────┘└───────┘└────┬─────┘│:8088│
           │        RabbitMQ                   │      └─────┘
           └──────────────────────────────────┘

管理后台前端  http://localhost:5174
（Vue 3 + Vite + Ant Design Vue 4，单独启动）

所有业务服务均注册至 Nacos，配置由 Nacos 配置中心统一下发
MySQL / Redis / RabbitMQ / MinIO 作为共享基础设施
OTLP → OpenObserve（链路追踪 + 结构化日志）
```

### 服务端口参考（本地开发）

| 服务 | 默认端口 |
|------|----------|
| gopair-gateway | 8081 |
| gopair-admin-service | 8088 |
| 其余业务服务 | `SERVER_PORT: 0`（随机端口，Nacos 自动发现） |
| 用户端前端 | 5173 |
| 管理后台前端 | 5174（独立启动） |

> 网关默认路由前缀：`/user`、`/room`、`/message`、`/file`、`/voice`、`/api/ws`、`/admin`

---

## 快速开始

### 1. 准备基础设施

确保以下组件已启动并可访问：

| 组件 | 版本要求 | 默认地址 |
|------|----------|----------|
| MySQL | 8.0+ | `localhost:3306` |
| Redis | 6.0+ | `localhost:6379` |
| RabbitMQ | 3.x | `localhost:5672` |
| Nacos | 2.x | `localhost:8848` |
| MinIO | 最新稳定版 | `localhost:9000` |

### 2. 初始化数据库

```bash
mysql -u root -p gopair < sql/init.sql
```

> 数据库名默认为 `gopair`，可通过 `MYSQL_DB` 环境变量覆盖。

### 3. 导入 Nacos 配置

登录 Nacos 控制台（`http://localhost:8848/nacos`），将 `nacos-configs/` 下的文件导入 `DEFAULT_GROUP`：

| 文件 | Data ID | 适用范围 |
|------|---------|----------|
| `gopair-shared.yml` | `gopair-shared.yml` | 所有业务服务（user/room/message/file/voice/websocket） |
| `gopair-gateway.yml` | `gopair-gateway.yml` | 仅 gateway |
| `gopair-logging-base.yml` | `gopair-logging-base.yml` | 所有服务 |

### 4. 配置环境变量

将以下变量写入 `.env` 或在启动前 export（括号内为默认值）：

```bash
# Nacos
NACOS_ADDR=127.0.0.1:8848
NACOS_GROUP=DEFAULT_GROUP
NACOS_NAMESPACE=

# MySQL
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DB=gopair

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=
REDIS_DB=0

# RabbitMQ
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
RABBITMQ_VHOST=/

# JWT（网关与 user-service 必须保持一致）
JWT_SECRET=<your-base64-encoded-secret>

# 网关鉴权白名单（逗号分隔，可追加）
GATEWAY_SKIP_AUTH_PATHS=/user/login,/user,/user/sendCode,/user/forgotPassword

# OpenTelemetry / OpenObserve（可选，未配置则日志/链路数据仅输出到本地文件）
OTLP_TRACING_URL=http://localhost:5081
OTLP_LOGGING_URL=http://localhost:5081
OTEL_AUTHORIZATION=Basic <your-base64-credentials>
OTEL_ORGANIZATION=default
OTEL_STREAM_NAME=${spring.application.name}
```

### 5. 构建与启动后端

```bash
# 在仓库根目录构建所有后端模块
mvn -pl apps/backend -am clean package -DskipTests

# 按需逐个启动微服务（建议顺序：gateway → user → room → message → file → voice → websocket → admin）
java -jar apps/backend/gopair-gateway/target/gopair-gateway-*.jar
java -jar apps/backend/gopair-user-service/target/gopair-user-service-*.jar
java -jar apps/backend/gopair-room-service/target/gopair-room-service-*.jar
java -jar apps/backend/gopair-message-service/target/gopair-message-service-*.jar
java -jar apps/backend/gopair-file-service/target/gopair-file-service-*.jar
java -jar apps/backend/gopair-voice-service/target/gopair-voice-service-*.jar
java -jar apps/backend/gopair-websocket-service/target/gopair-websocket-service-*.jar
java -jar apps/backend/gopair-admin-service/target/gopair-admin-service-*.jar
```

或使用 Maven Spring Boot 插件（开发模式）：

```bash
mvn -pl apps/backend/gopair-gateway spring-boot:run
mvn -pl apps/backend/gopair-user-service spring-boot:run
# ... 其余服务同理
```

### 6. 启动前端

用户端（`apps/frontend`）与管理端（`apps/backend/gopair-admin-service/admin-frontend`）各自独立启动：

```bash
# 用户端
cd apps/frontend
pnpm install
pnpm dev

# 管理后台（另一个终端）
cd apps/backend/gopair-admin-service/admin-frontend
pnpm install
pnpm dev
```

默认开发地址：`http://localhost:5173`（用户端）和 `http://localhost:5174`（管理后台），前端通过 `.env.local` 文件设置后端网关地址：

```bash
# apps/frontend/.env.local
VITE_API_BASE=http://localhost:8081
```

### 7. 常用入口

| 入口 | 地址 |
|------|------|
| 用户端应用 | `http://localhost:5173` |
| 管理后台 | `http://localhost:5174` |
| Knife4j API 文档（以 user-service 为例） | `http://localhost:<port>/doc.html` |
| 网关健康检查 | `http://localhost:8081/actuator/health` |
| Nacos 控制台 | `http://localhost:8848/nacos` |
| MinIO 控制台 | `http://localhost:9001` |
| OpenObserve（需自行部署） | `http://localhost:5081` |

---

## Nacos 配置说明

### gopair-shared.yml（业务服务公共配置）

- **数据源**：`MYSQL_HOST`、`MYSQL_PORT`、`MYSQL_DB` 环境变量覆盖
- **Redis**：`REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`、`REDIS_DB` 覆盖，Lettuce 连接池 max-active=16
- **RabbitMQ**：`RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST` 覆盖，prefetch=50，最大并发=8，AUTO ACK
- **MyBatis Plus**：下划线转驼峰，mapper 路径 `classpath*:/mapper/**/*.xml`，雪花 ID 自增
- **Knife4j**：`KNIFE4J_ENABLE` / `KNIFE4J_PRODUCTION` 控制开关与生产模式

### gopair-gateway.yml（网关专属配置）

- **Nacos 发现**：心跳间隔 5s，超时 15s，临时实例，支持 watch 动态感知
- **LoadBalancer**：启用重试，health-check initial-delay=0
- **JWT**：`JWT_SECRET` 环境变量注入，与 user-service 保持一致
- **白名单**：`GATEWAY_SKIP_AUTH_PATHS` 覆盖，默认放行 `/user/login`、`/user`（注册）、`/user/sendCode`、`/user/forgotPassword`、`/doc.html`、`/v3/api-docs`
- **Actuator**：暴露 `health,info,metrics,prometheus,gateway` 端点

### gopair-logging-base.yml（日志与链路追踪）

- **日志格式**：统一日志格式与 MDC 链路追踪字段（traceId、spanId、userId、nickname）
- **链路追踪**：Micrometer Tracing Bridge Brave + W3CTraceContextPropagator，采样率 100%
- **OTLP 端点**：`OTLP_TRACING_URL` / `OTLP_LOGGING_URL` 环境变量覆盖，默认 `http://localhost:5081`
- **OpenObserve 集成**：
  - `OTEL_AUTHORIZATION`：Basic Auth 凭证（Base64 编码）
  - `OTEL_ORGANIZATION`：组织名称（默认 `default`）
  - `OTEL_STREAM_NAME`：Stream 名（默认使用 `spring.application.name`）
  - 链路追踪（traces）和日志（logs）均通过 OTLP gRPC 发送至 OpenObserve
- **Prometheus 指标**：`PROMETHEUS_ENABLED=true`，暴露 `/actuator/prometheus` 端点
- **运维日志**：`gopair.logging.ops.*` 系列开关（mvc、actuator、health、metrics）

---

## API 概览

### User Service（`/user`）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/user` | 注册（需验证码） | 否 |
| POST | `/user/login` | 登录，返回 JWT | 否 |
| POST | `/user/sendCode` | 发送邮箱验证码（register / resetPassword） | 否 |
| POST | `/user/forgotPassword` | 忘记密码重置 | 否 |
| GET | `/user/{userId}` | 查询用户信息 | 是 |
| PUT | `/user` | 更新用户信息（昵称/邮箱/密码） | 是 |
| DELETE | `/user/{userId}` | 删除用户 | 是 |
| POST | `/user/cancel` | 注销账号（软删除） | 是 |

### Room Service（`/room`）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/room` | 创建房间 | 是 |
| POST | `/room/join` | 同步加入房间 | 是 |
| POST | `/room/join/async` | 异步加入房间，返回 acceptToken | 是 |
| GET | `/room/join/result?token=` | 轮询异步加入结果 | 是 |
| POST | `/room/{roomId}/leave` | 离开房间 | 是 |
| POST | `/room/{roomId}/close` | 关闭房间（仅房主） | 是 |
| GET | `/room/code/{roomCode}` | 按房间码查询房间 | 是 |
| GET | `/room/{roomId}/members` | 获取房间成员列表 | 是 |
| GET | `/room/my` | 获取我的房间列表（分页） | 是 |
| PATCH | `/room/{roomId}/password` | 更新房间密码/令牌模式（仅房主） | 是 |
| GET | `/room/{roomId}/password/current` | 获取当前密码/令牌（仅房主） | 是 |
| DELETE | `/room/{roomId}/members/{userId}` | 踢出成员（仅房主） | 是 |

### Message Service（`/message`）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/message` | 发送消息 | 是 |
| GET | `/message/room/{roomId}` | 查询房间历史消息（分页） | 是 |

### File Service（`/file`）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/file/avatar` | 上传用户头像（自动压缩 200×200） | 是 |
| POST | `/file/upload` | 上传文件到房间（图片自动生成缩略图） | 是 |
| GET | `/file/room/{roomId}` | 获取房间文件列表（分页） | 是 |
| GET | `/file/{fileId}` | 获取文件信息 | 是 |
| GET | `/file/{fileId}/download` | 生成下载 Presigned URL | 是 |
| GET | `/file/{fileId}/preview` | 预览文件（302 重定向） | 是 |
| DELETE | `/file/{fileId}` | 删除文件（仅上传者） | 是 |
| GET | `/file/room/{roomId}/stats` | 房间文件统计 | 是 |
| POST | `/file/room/{roomId}/cleanup` | 清理房间所有文件 | 是 |

### Voice Service（`/voice`）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/voice/room/{roomId}/join` | 加入或创建通话（按需创建） | 是 |
| POST | `/voice/{callId}/ready` | 通知 WebRTC 就绪，触发 participant-join 广播 | 是 |
| POST | `/voice/{callId}/join` | 通过 callId 加入通话 | 是 |
| POST | `/voice/{callId}/leave` | 离开通话 | 是 |
| POST | `/voice/{callId}/owner-leave` | 房主退出通话（通话不终止） | 是 |
| POST | `/voice/{callId}/end` | 结束通话 | 是 |
| GET | `/voice/{callId}` | 查询通话信息 | 是 |
| GET | `/voice/room/{roomId}/active` | 获取房间当前活跃通话 | 是 |
| POST | `/voice/signaling` | 转发 WebRTC 信令 | 是 |

### Admin Service（`/admin`，端口 8088）

基础路径：`http://localhost:8088/admin`，JWT Bearer Token 鉴权（独立鉴权体系）。

#### 管理员认证

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/admin/auth/login` | 管理员登录 | 否 |

#### 仪表盘

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/dashboard/stats` | 全局统计数据（用户/房间/消息/通话时长） | 是 |

#### 用户管理

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/users/page` | 分页查询用户（keyword 搜索） | 是 |
| GET | `/admin/users/{userId}` | 用户详情（+ 房间数统计） | 是 |
| POST | `/admin/users/{userId}/disable` | 停用用户 | 是 |
| POST | `/admin/users/{userId}/enable` | 启用用户 | 是 |

#### 房间管理

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/rooms/page` | 分页查询房间（status/keyword 过滤） | 是 |
| GET | `/admin/rooms/{roomId}` | 房间详情（+ 成员列表 + userMap） | 是 |
| POST | `/admin/rooms/{roomId}/close` | 强制关闭房间 | 是 |

#### 消息管理

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/messages/page` | 分页查询消息（roomId/keyword 过滤） | 是 |
| GET | `/admin/messages/room/{roomId}` | 按房间查询消息 | 是 |

#### 文件管理

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/files/page` | 分页查询文件（roomId/keyword 过滤） | 是 |
| GET | `/admin/files/{fileId}` | 文件详情 | 是 |
| POST | `/admin/files/{fileId}/delete` | 删除文件元数据 | 是 |

#### 通话记录

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/voice-calls/page` | 分页查询通话记录（roomId/status 过滤） | 是 |
| GET | `/admin/voice-calls/{callId}` | 通话详情 | 是 |
| GET | `/admin/voice-calls/{callId}/participants` | 查询通话参与者 | 是 |

#### 审计日志

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| GET | `/admin/audit-logs/page` | 分页查询审计日志（adminId/operation/targetType 过滤） | 是 |

---

## WebSocket 消息协议

WebSocket 服务地址（经网关）：`ws://localhost:8081/api/ws`

### 连接认证

连接建立后发送 `AUTH` 消息携带 JWT Token：

```json
{
  "type": "auth",
  "payload": { "token": "<JWT>" }
}
```

### 消息类型（MessageType）

| 类型 | 值 | 说明 |
|------|-----|------|
| AUTH | `auth` | 连接认证 |
| SUBSCRIBE | `subscribe` | 订阅频道（如房间频道） |
| UNSUBSCRIBE | `unsubscribe` | 取消订阅频道 |
| SUBSCRIBE_RESPONSE | `subscribe_response` | 订阅操作响应 |
| SUBSCRIPTION_UPDATE | `subscription_update` | 订阅状态变更通知 |
| CHAT | `chat` | 聊天消息 |
| SIGNALING | `signaling` | WebRTC 信令 |
| FILE | `file` | 文件相关事件 |
| SYSTEM | `system` | 系统消息 |
| HEARTBEAT | `heartbeat` | 心跳保活 |
| CONNECTION | `connection` | 连接状态 |
| CHANNEL_MESSAGE | `channel_message` | 房间频道内各类业务事件 |
| GLOBAL_NOTIFICATION | `global_notification` | 全局通知 |
| ERROR | `error` | 错误消息 |

### 频道订阅示例

```json
{
  "type": "subscribe",
  "payload": { "channel": "room:123456" }
}
```

### 限流机制

WebSocket 服务内置 Token Bucket 限流（`TokenBucketRateLimitService`），超频连接将收到 `ERROR` 类型响应并断开。

---

## 前端结构

本项目包含两套独立的前端应用，分别面向用户和管理员。

### 用户端（`apps/frontend/`）

```
apps/frontend/src/
├── api/                # HTTP 接口封装
│   ├── auth.ts         # 登录、注册、验证码
│   ├── room.ts         # 房间相关接口
│   ├── message.ts      # 消息接口
│   ├── file.ts         # 文件上传/下载接口
│   ├── voice.ts        # 语音通话接口
│   └── ai.ts           # AI 助手接口
├── components/
│   ├── chat/           # 聊天消息、输入框、Emoji
│   ├── file/           # 文件列表、上传区、预览弹窗
│   ├── voice/          # 语音通话面板、通话详情
│   ├── ai/             # AI Chat 抽屉
│   ├── LoginForm.vue
│   ├── CreateRoomModal.vue
│   ├── JoinRoomModal.vue
│   ├── RoomCard.vue
│   └── UserProfileModal.vue
├── composables/
│   ├── useWebSocket.ts       # 基础 WebSocket 封装
│   ├── useRoomWebSocket.ts   # 房间频道 WebSocket
│   ├── useVoiceWebSocket.ts  # 语音信令 WebSocket
│   └── useVoiceCall.ts       # 语音通话逻辑
├── stores/
│   ├── auth.ts         # 用户认证状态（Pinia）
│   ├── room.ts         # 房间状态
│   └── websocket.ts    # WebSocket 连接状态
├── utils/
│   ├── request.ts      # Axios 封装（拦截器、Token 注入）
│   ├── storage.ts      # 本地存储工具
│   └── webrtc/         # WebRTC 核心模块
│       ├── WebRTCManager.ts
│       ├── P2PConnectionManager.ts
│       ├── AudioDeviceManager.ts
│       └── SignalingProtocol.ts
├── views/
│   ├── LoginView.vue
│   ├── RoomsView.vue
│   └── RoomDetailView.vue
└── types/              # TypeScript 类型定义
    ├── auth.ts
    ├── room.ts
    ├── websocket.ts
    ├── ai.ts
    └── api.ts
```

### 管理后台（`apps/backend/gopair-admin-service/admin-frontend/`）

```
admin-frontend/src/
├── api/                # HTTP 接口封装
│   ├── auth.ts         # 管理员登录
│   ├── dashboard.ts     # 仪表盘统计
│   ├── users.ts        # 用户管理
│   ├── rooms.ts        # 房间管理
│   ├── messages.ts     # 消息管理
│   ├── files.ts        # 文件管理
│   ├── voiceCalls.ts   # 通话记录
│   └── auditLogs.ts    # 审计日志
├── components/
│   ├── common/          # ConfirmModal、PageHeader、StatCard、StatusBadge
│   └── layout/          # AppHeader、AppSidebar、AppLayout
├── views/
│   ├── LoginView.vue
│   ├── DashboardView.vue
│   ├── users/UserManageView.vue
│   ├── rooms/RoomManageView.vue
│   ├── messages/MessageManageView.vue
│   ├── files/FileManageView.vue
│   ├── voiceCalls/VoiceCallView.vue
│   └── auditLogs/AuditLogView.vue
├── stores/
│   ├── auth.ts         # 管理员认证状态（Pinia）
│   └── app.ts          # 菜单/布局状态
├── router/index.ts
├── types/index.ts
└── utils/
    ├── request.ts      # Axios 封装（管理员 Token 注入）
    └── format.ts       # 格式化工具
```

---

## 性能与压测

`perf/k6/` 目录提供完整的 K6 压测方案，涵盖房间、消息、登录、WebSocket 分发层等 8 个压测场景，配套 InfluxDB + Grafana 可视化。

### 场景概览

| 场景 | 脚本 | 目标 |
|------|------|------|
| 场景1 | `scenarios/capacity.js` | 容量验证：M >> N 下 JOINED 人数严格等于房间容量（防超卖脉冲测试） |
| 场景2 | `scenarios/throughput.js` | 持续吞吐：join → poll → leave 循环，测量 RT / TPS / 系统稳定性 |
| 场景3 | `scenarios/message_basic.js` | 消息发送：单用户高频 / 群聊并发 / 可靠性验证（零丢失） |
| 场景4 | `scenarios/boundary.js` | 容量边界：满员后降级行为，验证 FULL/FAILED 响应率合理性 |
| 场景6 | `auth/auth_pulse.js` | 登录脉冲：M 个并发 VU 同时登录，测量最大并发承受量 |
| 场景7 | `auth/auth_sustained.js` | 持续登录吞吐：长时间高负载下登录接口 TPS 与 RT 稳定性 |
| 场景8 | `scenarios/message_ws_fanout.js` | WebSocket Fan-out：ChannelMessageRouter 在 N 人房间下的分发能力 |

详细命令见 `perf/k6/README.md`。

### 前置准备

```bash
# 安装 K6（Windows）
winget install k6

# 生成测试用户（生成 200 条数据到 perf/user_info.txt）
node perf/scripts/bootstrap_users_and_tokens.js --count 200
```

### 快速运行示例

```bash
# 快速验证防超卖（10 容量，100 并发）
k6 run -e BASE_URL=http://localhost:8081 -e CAPACITY=10 -e VUS=100 scenarios/capacity.js

# 消息群聊压测（50 VU）
k6 run -e BASE_URL=http://localhost:8081 -e MODE=group_chat -e VUS=50 scenarios/message_basic.js

# 登录脉冲摸上限（200 并发）
k6 run -e BASE_URL=http://localhost:8081 -e AUTH_USER_COUNT=200 auth/auth_pulse.js

# 导出 JSON 格式结果
k6 run --out json=results.json scenarios/throughput.js
```

### InfluxDB + Grafana 可视化

```bash
# 启动监控栈
cd perf/k6 && docker compose up -d

# 运行压测并输出到 InfluxDB
k6 run --out influxdb=http://localhost:8086 scenarios/throughput.js

# 访问 Grafana（admin / adminpassword）
# 导入 K6 官方仪表盘（ID: 2587）
```

目录结构：

```
perf/k6/
├── common.js                      # 共享配置与辅助函数
├── auth/
│   ├── common_auth.js            # auth 共享凭证模块
│   ├── auth_pulse.js            # 场景6：登录脉冲
│   └── auth_sustained.js        # 场景7：持续登录吞吐
├── scenarios/
│   ├── capacity.js               # 场景1：容量验证
│   ├── throughput.js            # 场景2：持续吞吐
│   ├── boundary.js              # 场景4：容量边界
│   ├── message_basic.js         # 场景3：消息压测
│   └── message_ws_fanout.js    # 场景8：WS Fan-out
├── scripts/
│   ├── ws_fanout_client.js     # Node.js WS 分发层测量客户端
│   ├── bootstrap_users_and_tokens.js
│   └── analyze.js
├── grafana-provisioning/        # Grafana 自动配置
├── docker-compose.yml           # InfluxDB + Grafana
└── README.md
```

---

## 常见开发动作

### 新增微服务

1. 在 `apps/backend/` 下创建新模块目录，添加 `pom.xml`
2. 在 `apps/backend/pom.xml` 的 `<modules>` 中声明新模块
3. 依赖 `gopair-common` 和 `gopair-framework-web` 复用通用能力
4. 在 `gopair-gateway` 的 Nacos 配置中追加路由规则
5. 为新服务在 Nacos 创建对应的配置文件（引用 `gopair-shared.yml` 共享配置）

### 新增管理后台模块

`gopair-admin-service` 为独立模块，同时包含后端（Java/Spring Boot）和前端（Vue 3 + Vite）：

1. 后端：在 `src/main/java/com/gopair/adminservice/` 下开发 Controller / Service，参考 `AdminAuditAspect` 实现 AOP 审计日志
2. 前端：在 `admin-frontend/src/views/` 下新增页面组件，在 `admin-frontend/src/api/` 下封装接口
3. 启动管理后台后端：`mvn -pl apps/backend/gopair-admin-service spring-boot:run` 或 `java -jar target/gopair-admin-service-*.jar`
4. 启动管理后台前端：`cd apps/backend/gopair-admin-service/admin-frontend && pnpm dev`（端口 5174）
5. 在 `gopair-gateway` 的路由配置中追加 `/admin/**` 规则（已有）

### 复用框架能力

**用户上下文**（需依赖 `gopair-framework-web`）：
```java
// 获取当前登录用户 ID 和昵称（由网关注入 Header，Filter 解析）
Long userId = UserContextHolder.getCurrentUserId();
String nickname = UserContextHolder.getCurrentNickname();
```

**业务日志 AOP**：
```java
// 在方法上添加注解，自动记录操作开始/结束/耗时/异常
@LogRecord(operation = "创建房间", module = "房间管理", logPerformance = true)
public RoomVO createRoom(...) { ... }
```

**统一响应体**：
```java
// 成功响应
return R.ok(data);
// 失败响应
return R.fail(errorCode);
```

**WebSocket 消息推送**（在任意业务服务中）：
```java
// 通过 RabbitMQ 向指定房间频道推送事件
webSocketMessageProducer.sendToChannel("room:" + roomId, payload);
```

### 本地多服务联调建议

- 使用 IntelliJ IDEA 的 **Run Dashboard** 或 **Services** 窗口同时启动多个 Spring Boot 服务
- 确保各服务已注册到本地 Nacos（`localhost:8848`），网关可通过负载均衡转发
- 前端 `VITE_API_BASE` 指向本地网关 `http://localhost:8081`
- 管理后台前端独立运行在 `localhost:5174`，后端直连 `localhost:8088`（不经过网关）

### 日志与排查

- `gopair-framework-web` 内置 `@LogRecord` AOP，关键业务操作自动记录模块、操作名、耗时及异常
- MDC 自动注入 `traceId`、`spanId`、`userId`、`nickname`，可在日志文件中跨服务追踪同一请求链路
- 链路追踪 + 结构化日志通过 OTLP 发送至 OpenObserve，可通过 `http://localhost:5081` 查看
- 动态调整日志级别：访问 `LogLevelController`（`/actuator/loggers`）或通过 Nacos 配置热更新
- WebSocket 服务可通过 `ScheduledTaskService` 定期清理失效会话与订阅

---

## 参考文档

| 文档 | 内容 |
|------|------|
| `CHANGELOG.md` | 项目变更日志，记录每次迭代的功能修复与新增 |
| `CACHE_AVALANCHE_IMPLEMENTATION.md` | 缓存雪崩防护策略实现细节 |
| `ROOM_CACHE_PROTECTION_ANALYSIS.md` | 房间缓存一致性与保护机制分析 |
| `REDISSON_VS_SPRING_REDIS.md` | Redisson 与 Spring Data Redis 选型对比 |
| `REDIS_CACHE_ANALYSIS.md` | Redis 缓存使用案例与性能分析 |

---

## 许可证

本项目采用 **MIT License**，欢迎提交 Issue / PR 共建。

反馈问题时请附上：运行环境（OS、JDK 版本）、已启动的服务、关键日志片段及复现步骤，便于快速定位。  