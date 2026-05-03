# GoPair 管理员服务接口文档

**服务名称**: gopair-admin-service
**服务端口**: 8088
**基础路径**: `http://localhost:8088/admin`
**认证方式**: JWT Bearer Token（通过 `Authorization: Bearer <token>` 请求头或 `admin_token` Cookie 传递）
**数据库**: MySQL（配置见 application-dev.yml）

---

## 目录

1. [通用说明](#通用说明)
2. [管理员认证](#1-管理员认证)
3. [仪表盘](#2-仪表盘)
4. [用户管理](#3-用户管理)
5. [房间管理](#4-房间管理)
6. [消息管理](#5-消息管理)
7. [文件管理](#6-文件管理)
8. [审计日志](#7-审计日志)
9. [通话记录](#8-通话记录)
10. [数据模型](#数据模型)

---

## 通用说明

### 统一响应格式

所有接口均使用 `R<T>` 统一封装返回值：

```json
{
  "code": 200,
  "msg": "成功",
  "data": { ... }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未登录或 token 失效 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

### 分页响应格式

分页查询接口统一使用 MyBatis-Plus `Page<T>` 结构：

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "records": [ ... ],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

### 认证要求

除登录接口外，所有 `/admin/**` 接口均需携带有效 JWT Token。

请求示例：

```bash
curl -X GET http://localhost:8088/admin/dashboard/stats \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

---

## 1. 管理员认证

**Controller**: `AdminAuthController`
**基础路径**: `/admin/auth`

### 1.1 管理员登录

登录接口，**无需认证**。

**请求**

```
POST /admin/auth/login
Content-Type: application/x-www-form-urlencoded
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | String | 是 | 管理员用户名 |
| password | String | 是 | 密码 |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6ImFkbWluIn0.xxx",
    "adminId": 1,
    "username": "admin",
    "nickname": "超级管理员"
  }
}
```

**失败响应（401）**

```json
{
  "code": 401,
  "msg": "密码错误",
  "data": null
}
```

可能的错误信息：`管理员账号不存在`、`管理员账号已被停用`、`密码错误`

---

## 2. 仪表盘

**Controller**: `DashboardController`
**基础路径**: `/admin/dashboard`

### 2.1 获取全局统计数据

获取系统级的统计汇总数据。

**请求**

```
GET /admin/dashboard/stats
```

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "totalUsers": 1520,
    "todayNewUsers": 23,
    "activeRooms": 108,
    "todayNewRooms": 15,
    "todayMessages": 0,
    "todayVoiceCallDuration": 18450
  }
}
```

**字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| totalUsers | Long | 用户总数 |
| todayNewUsers | Long | 今日新增用户数 |
| activeRooms | Long | 当前活跃房间数（status = 0） |
| todayNewRooms | Long | 今日新增房间数 |
| todayMessages | Long | 今日消息数（当前恒为 0，未实现统计） |
| todayVoiceCallDuration | Long | 今日语音通话总时长（秒），仅统计已结束（status=2）的通话 |

---

## 3. 用户管理

**Controller**: `UserManageController`
**基础路径**: `/admin/users`

### 3.1 分页查询用户

**请求**

```
GET /admin/users/page
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页条数 |
| keyword | String | 否 | null | 搜索关键词，匹配 nickname 或 email |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "records": [
      {
        "userId": 1,
        "nickname": "张三",
        "password": "$2a$10$...",
        "email": "zhangsan@example.com",
        "avatar": "https://...",
        "status": "0",
        "remark": "",
        "createTime": "2026-01-15 10:30:00",
        "updateTime": "2026-04-10 08:20:00"
      }
    ],
    "total": 1520,
    "size": 20,
    "current": 1,
    "pages": 76
  }
}
```

### 3.2 用户详情

**请求**

```
GET /admin/users/{userId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "user": {
      "userId": 1,
      "nickname": "张三",
      "email": "zhangsan@example.com",
      "avatar": "https://...",
      "status": "0",
      "remark": "",
      "createTime": "2026-01-15 10:30:00",
      "updateTime": "2026-04-10 08:20:00"
    },
    "roomCount": 5,
    "ownedRoomCount": 2
  }
}
```

**失败响应（404）**

```json
{
  "code": 404,
  "msg": "用户不存在",
  "data": null
}
```

### 3.3 停用用户

将用户状态设置为 `1`，被停用的用户将无法登录。

**请求**

```
POST /admin/users/{userId}/disable
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": null
}
```

此操作会触发审计日志记录（operation = `USER_DISABLE`，targetType = `USER`）。

### 3.4 启用用户

将用户状态设置为 `0`。

**请求**

```
POST /admin/users/{userId}/enable
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": null
}
```

此操作会触发审计日志记录（operation = `USER_ENABLE`，targetType = `USER`）。

---

## 4. 房间管理

**Controller**: `RoomManageController`
**基础路径**: `/admin/rooms`

### 4.1 分页查询房间

**请求**

```
GET /admin/rooms/page
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页条数 |
| status | Integer | 否 | null | 房间状态过滤（0=活跃，1=已关闭） |
| keyword | String | 否 | null | 搜索关键词，匹配 roomName 或 roomCode |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "records": [
      {
        "roomId": 10,
        "roomCode": "ABC123",
        "roomName": "前端技术交流",
        "description": "讨论前端最新技术",
        "maxMembers": 50,
        "currentMembers": 12,
        "ownerId": 1,
        "status": 0,
        "expireTime": "2026-12-31 23:59:59",
        "version": 3,
        "passwordMode": 0,
        "passwordHash": null,
        "passwordVisible": 1,
        "createTime": "2026-03-01 09:00:00",
        "updateTime": "2026-04-15 14:30:00"
      }
    ],
    "total": 108,
    "size": 20,
    "current": 1,
    "pages": 6
  }
}
```

### 4.2 房间详情

**请求**

```
GET /admin/rooms/{roomId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| roomId | Long | 是 | 房间 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "room": {
      "roomId": 10,
      "roomCode": "ABC123",
      "roomName": "前端技术交流",
      "description": "讨论前端最新技术",
      "maxMembers": 50,
      "currentMembers": 12,
      "ownerId": 1,
      "status": 0,
      "expireTime": "2026-12-31 23:59:59",
      "version": 3,
      "passwordMode": 0,
      "passwordHash": null,
      "passwordVisible": 1,
      "createTime": "2026-03-01 09:00:00",
      "updateTime": "2026-04-15 14:30:00"
    },
    "members": [
      {
        "id": 1,
        "roomId": 10,
        "userId": 2,
        "role": 1,
        "status": 0,
        "joinTime": "2026-03-01 09:05:00",
        "lastActiveTime": "2026-04-15 10:00:00",
        "createTime": "2026-03-01 09:05:00",
        "updateTime": "2026-04-15 10:00:00"
      }
    ],
    "userMap": {
      "2": {
        "userId": 2,
        "nickname": "李四",
        "email": "lisi@example.com",
        "avatar": "https://...",
        "status": "0",
        "createTime": "2026-01-10 08:00:00",
        "updateTime": "2026-04-10 12:00:00"
      }
    }
  }
}
```

其中 `userMap` 以 userId 为 key 建立了成员用户的信息索引，便于前端快速渲染成员昵称和头像。

**失败响应（404）**

```json
{
  "code": 404,
  "msg": "房间不存在",
  "data": null
}
```

### 4.3 强制关闭房间

将房间状态设置为 `1`（已关闭）。

**请求**

```
POST /admin/rooms/{roomId}/close
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| roomId | Long | 是 | 房间 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": null
}
```

此操作会触发审计日志记录（operation = `ROOM_CLOSE`，targetType = `ROOM`）。

---

## 5. 消息管理

**Controller**: `MessageManageController`
**基础路径**: `/admin/messages`

### 5.1 分页查询消息

**请求**

```
GET /admin/messages/page
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页条数 |
| roomId | Long | 否 | null | 房间 ID 过滤 |
| keyword | String | 否 | null | 搜索关键词，匹配消息内容 |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "records": [
      {
        "messageId": 100,
        "roomId": 10,
        "senderId": 2,
        "messageType": 1,
        "content": "大家好，欢迎来到前端技术交流群",
        "fileUrl": null,
        "fileName": null,
        "fileSize": null,
        "replyToId": null,
        "createTime": "2026-04-15 09:00:00",
        "updateTime": "2026-04-15 09:00:00"
      }
    ],
    "total": 500,
    "size": 20,
    "current": 1,
    "pages": 25
  }
}
```

**字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| messageId | Long | 消息 ID |
| roomId | Long | 所属房间 ID |
| senderId | Long | 发送者用户 ID |
| messageType | Integer | 消息类型（1=文本，2=图片，3=文件，4=语音等，具体枚举需参考前端常量定义） |
| content | String | 消息文本内容 |
| fileUrl | String | 附件 URL（无附件时为 null） |
| fileName | String | 附件文件名 |
| fileSize | Long | 附件大小（字节） |
| replyToId | Long | 回复的消息 ID（无回复时为 null） |
| createTime | String | 发送时间（yyyy-MM-dd HH:mm:ss） |

### 5.2 按房间查询消息

获取指定房间的消息列表（按时间倒序）。

**请求**

```
GET /admin/messages/room/{roomId}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| roomId | Long | 是 | - | 房间 ID（路径参数） |
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 50 | 每页条数 |

**响应格式** 同 5.1 分页查询消息。

---

## 6. 文件管理

**Controller**: `FileManageController`
**基础路径**: `/admin/files`

### 6.1 分页查询文件

**请求**

```
GET /admin/files/page
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页条数 |
| roomId | Long | 否 | null | 房间 ID 过滤 |
| keyword | String | 否 | null | 搜索关键词，匹配 fileName |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "records": [
      {
        "fileId": 50,
        "roomId": 10,
        "uploaderId": 2,
        "uploaderNickname": "李四",
        "fileName": "架构设计图.png",
        "filePath": "/uploads/2026/04/15/xxx.png",
        "fileSize": 2048000,
        "thumbnailSize": 153600,
        "fileType": "png",
        "contentType": "image/png",
        "downloadCount": 15,
        "uploadTime": "2026-04-15 10:30:00",
        "createTime": "2026-04-15 10:30:00",
        "updateTime": "2026-04-15 10:30:00"
      }
    ],
    "total": 230,
    "size": 20,
    "current": 1,
    "pages": 12
  }
}
```

### 6.2 文件详情

**请求**

```
GET /admin/files/{fileId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| fileId | Long | 是 | 文件 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "fileId": 50,
    "roomId": 10,
    "uploaderId": 2,
    "uploaderNickname": "李四",
    "fileName": "架构设计图.png",
    "filePath": "/uploads/2026/04/15/xxx.png",
    "fileSize": 2048000,
    "thumbnailSize": 153600,
    "fileType": "png",
    "contentType": "image/png",
    "downloadCount": 15,
    "uploadTime": "2026-04-15 10:30:00",
    "createTime": "2026-04-15 10:30:00",
    "updateTime": "2026-04-15 10:30:00"
  }
}
```

**失败响应（404）**

```json
{
  "code": 404,
  "msg": "文件记录不存在",
  "data": null
}
```

### 6.3 删除文件元数据

删除文件的数据库元数据记录（不删除实际文件存储）。此操作会触发审计日志。

**请求**

```
POST /admin/files/{fileId}/delete
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| fileId | Long | 是 | 文件 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": null
}
```

此操作会触发审计日志记录（operation = `FILE_DELETE`，targetType = `FILE`）。

---

## 7. 审计日志

**Controller**: `AuditLogController`
**基础路径**: `/admin/audit-logs`

### 7.1 分页查询审计日志

查看管理员在系统中的操作记录。

**请求**

```
GET /admin/audit-logs/page
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页条数 |
| adminId | Long | 否 | null | 管理员 ID 过滤 |
| operation | String | 否 | null | 操作类型过滤（如 USER_DISABLE、ROOM_CLOSE） |
| targetType | String | 否 | null | 目标类型过滤（如 USER、ROOM、FILE） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "records": [
      {
        "id": 100,
        "adminId": 1,
        "adminUsername": "admin",
        "operation": "USER_DISABLE",
        "targetType": "USER",
        "targetId": "5",
        "detail": "{\"method\":\"disableUser\",\"args\":[5],\"result\":\"...\"}",
        "ipAddress": "192.168.1.100",
        "userAgent": "Mozilla/5.0...",
        "createTime": "2026-04-15 11:00:00"
      }
    ],
    "total": 450,
    "size": 20,
    "current": 1,
    "pages": 23
  }
}
```

**字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 日志记录 ID |
| adminId | Long | 操作用户 ID |
| adminUsername | String | 操作用户名 |
| operation | String | 操作类型，见下表 |
| targetType | String | 目标资源类型 |
| targetId | String | 目标资源 ID |
| detail | String | 操作详情（JSON，包含方法名、参数、结果） |
| ipAddress | String | 客户端 IP 地址 |
| userAgent | String | 浏览器客户端标识 |
| createTime | String | 操作时间 |

**已定义的操作类型**

| operation | targetType | 触发条件 |
|-----------|-----------|---------|
| USER_DISABLE | USER | 停用用户 |
| USER_ENABLE | USER | 启用用户 |
| ROOM_CLOSE | ROOM | 强制关闭房间 |
| FILE_DELETE | FILE | 删除文件元数据 |

---

## 8. 通话记录

**Controller**: `VoiceCallController`
**基础路径**: `/admin/voice-calls`

### 8.1 分页查询通话记录

**请求**

```
GET /admin/voice-calls/page
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码 |
| pageSize | Integer | 否 | 20 | 每页条数 |
| roomId | Long | 否 | null | 房间 ID 过滤 |
| status | Integer | 否 | null | 通话状态（0=进行中，1=已取消，2=已结束） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "records": [
      {
        "callId": 30,
        "roomId": 10,
        "initiatorId": 1,
        "callType": 1,
        "status": 2,
        "startTime": "2026-04-15 14:00:00",
        "endTime": "2026-04-15 14:30:00",
        "duration": 1800,
        "isAutoCreated": false
      }
    ],
    "total": 85,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

**字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| callId | Long | 通话记录 ID |
| roomId | Long | 所属房间 ID |
| initiatorId | Long | 发起人用户 ID |
| callType | Integer | 通话类型（1=语音，2=视频，需确认） |
| status | Integer | 状态（0=进行中，1=已取消，2=已结束） |
| startTime | String | 开始时间 |
| endTime | String | 结束时间 |
| duration | Integer | 通话时长（秒），仅 status=2 时有意义 |
| isAutoCreated | Boolean | 是否由系统自动创建 |

### 8.2 通话详情

**请求**

```
GET /admin/voice-calls/{callId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| callId | Long | 是 | 通话记录 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": {
    "callId": 30,
    "roomId": 10,
    "initiatorId": 1,
    "callType": 1,
    "status": 2,
    "startTime": "2026-04-15 14:00:00",
    "endTime": "2026-04-15 14:30:00",
    "duration": 1800,
    "isAutoCreated": false
  }
}
```

**失败响应（404）**

```json
{
  "code": 404,
  "msg": "通话记录不存在",
  "data": null
}
```

### 8.3 查询通话参与者

获取指定通话的所有参与者信息。

**请求**

```
GET /admin/voice-calls/{callId}/participants
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| callId | Long | 是 | 通话记录 ID（路径参数） |

**响应示例**

```json
{
  "code": 200,
  "msg": "成功",
  "data": [
    {
      "id": 1,
      "callId": 30,
      "userId": 1,
      "joinTime": "2026-04-15 14:00:05",
      "leaveTime": "2026-04-15 14:30:00",
      "connectionStatus": 1
    },
    {
      "id": 2,
      "callId": 30,
      "userId": 2,
      "joinTime": "2026-04-15 14:00:30",
      "leaveTime": "2026-04-15 14:25:00",
      "connectionStatus": 1
    }
  ]
}
```

**字段说明**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 参与者记录 ID |
| callId | Long | 通话 ID |
| userId | Long | 用户 ID |
| joinTime | String | 加入时间 |
| leaveTime | String | 离开时间（未离开为 null） |
| connectionStatus | Integer | 连接状态（1=正常，0=断线，需确认） |

---

## 数据模型

### User（用户）

对应表 `user`，继承 `BaseEntity`。

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | Long | 主键 |
| nickname | String | 昵称 |
| password | String | 密码（BCrypt 加密） |
| email | String | 邮箱 |
| avatar | String | 头像 URL |
| status | Character | 状态（'0'=正常，'1'=停用） |
| remark | String | 备注 |

### Room（房间）

对应表 `room`，继承 `BaseEntity`。

| 字段 | 类型 | 说明 |
|------|------|------|
| roomId | Long | 主键 |
| roomCode | String | 房间邀请码 |
| roomName | String | 房间名称 |
| description | String | 房间描述 |
| maxMembers | Integer | 最大成员数 |
| currentMembers | Integer | 当前成员数 |
| ownerId | Long | 房主用户 ID |
| status | Integer | 状态（0=活跃，1=已关闭） |
| expireTime | LocalDateTime | 过期时间 |
| version | Integer | 乐观锁版本号 |
| passwordMode | Integer | 密码模式（0=无密码） |
| passwordHash | String | 房间密码哈希 |
| passwordVisible | Integer | 密码是否可见 |

### RoomMember（房间成员）

对应表 `room_member`，继承 `BaseEntity`。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| roomId | Long | 房间 ID |
| userId | Long | 用户 ID |
| role | Integer | 角色（1=普通成员，2=管理员，0=房主？需确认） |
| status | Integer | 状态 |
| joinTime | LocalDateTime | 加入时间 |
| lastActiveTime | LocalDateTime | 最后活跃时间 |

### Message（消息）

对应表 `message`，继承 `BaseEntity`。

| 字段 | 类型 | 说明 |
|------|------|------|
| messageId | Long | 主键 |
| roomId | Long | 房间 ID |
| senderId | Long | 发送者用户 ID |
| messageType | Integer | 消息类型 |
| content | String | 消息文本 |
| fileUrl | String | 附件 URL |
| fileName | String | 附件名 |
| fileSize | Long | 附件大小 |
| replyToId | Long | 回复的消息 ID |

### RoomFile（房间文件）

对应表 `room_file`，继承 `BaseEntity`。

| 字段 | 类型 | 说明 |
|------|------|------|
| fileId | Long | 主键 |
| roomId | Long | 房间 ID |
| uploaderId | Long | 上传者用户 ID |
| uploaderNickname | String | 上传者昵称 |
| fileName | String | 文件名 |
| filePath | String | 文件存储路径 |
| fileSize | Long | 文件大小（字节） |
| thumbnailSize | Long | 缩略图字节数（图片类型有值，非图片为0） |
| fileType | String | 文件类型扩展名 |
| contentType | String | MIME 类型 |
| downloadCount | Integer | 下载次数 |
| uploadTime | LocalDateTime | 上传时间 |

### AdminAuditLog（审计日志）

对应表 `admin_audit_log`，**不继承 BaseEntity**。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| adminId | Long | 管理员 ID |
| adminUsername | String | 管理员用户名 |
| operation | String | 操作类型 |
| targetType | String | 目标类型 |
| targetId | String | 目标 ID |
| detail | String | 操作详情（JSON） |
| ipAddress | String | 客户端 IP |
| userAgent | String | 客户端 UA |
| createTime | LocalDateTime | 操作时间 |

### VoiceCall（语音通话）

对应表 `voice_call`，**不继承 BaseEntity**。

| 字段 | 类型 | 说明 |
|------|------|------|
| callId | Long | 主键 |
| roomId | Long | 房间 ID |
| initiatorId | Long | 发起人用户 ID |
| callType | Integer | 通话类型 |
| status | Integer | 状态（0=进行中，1=已取消，2=已结束） |
| startTime | LocalDateTime | 开始时间 |
| endTime | LocalDateTime | 结束时间 |
| duration | Integer | 通话时长（秒） |
| isAutoCreated | Boolean | 是否系统自动创建 |

### VoiceCallParticipant（通话参与者）

对应表 `voice_call_participant`，**不继承 BaseEntity**。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 主键 |
| callId | Long | 通话 ID |
| userId | Long | 用户 ID |
| joinTime | LocalDateTime | 加入时间 |
| leaveTime | LocalDateTime | 离开时间 |
| connectionStatus | Integer | 连接状态 |

---

## 附录

### A. 前端管理员界面建议的页面结构

基于上述接口分析，建议管理员界面包含以下页面：

| 页面 | 核心功能 | 对应接口 |
|------|---------|---------|
| 登录页 | 管理员登录 | POST /admin/auth/login |
| 仪表盘 | 统计概览 | GET /admin/dashboard/stats |
| 用户管理 | 列表、搜索、详情、停用/启用 | GET /admin/users/page、GET /admin/users/{id}、POST /admin/users/{id}/disable、POST /admin/users/{id}/enable |
| 房间管理 | 列表、搜索、详情、强制关闭 | GET /admin/rooms/page、GET /admin/rooms/{id}、POST /admin/rooms/{id}/close |
| 消息管理 | 按房间查看消息 | GET /admin/messages/page、GET /admin/messages/room/{roomId} |
| 文件管理 | 列表、详情、删除 | GET /admin/files/page、GET /admin/files/{id}、POST /admin/files/{id}/delete |
| 审计日志 | 操作记录查询 | GET /admin/audit-logs/page |
| 通话记录 | 通话列表、详情、参与者 | GET /admin/voice-calls/page、GET /admin/voice-calls/{id}、GET /admin/voice-calls/{id}/participants |

### B. 枚举值参考

| 枚举项 | 字段 | 值 | 说明 |
|--------|------|-----|------|
| 用户状态 | User.status | '0' | 正常 |
| 用户状态 | User.status | '1' | 停用 |
| 房间状态 | Room.status | 0 | 活跃 |
| 房间状态 | Room.status | 1 | 已关闭 |
| 管理员状态 | AdminUser.status | 0 | 正常 |
| 管理员状态 | AdminUser.status | 1 | 停用 |
| 通话状态 | VoiceCall.status | 0 | 进行中 |
| 通话状态 | VoiceCall.status | 1 | 已取消 |
| 通话状态 | VoiceCall.status | 2 | 已结束 |
| 连接状态 | VoiceCallParticipant.connectionStatus | 0 | 断线 |
| 连接状态 | VoiceCallParticipant.connectionStatus | 1 | 正常 |
