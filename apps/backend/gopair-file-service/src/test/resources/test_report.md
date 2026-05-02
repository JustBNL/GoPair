# 文件服务测试报告

## 测试模块

`gopair-file-service` - GoPair 文件服务模块

## 测试策略

- **集成测试**：真实 MySQL (`gopair_test`) + 真实 Redis + Mock MinIO/MQ/WebSocket
- **事务回滚**：所有测试方法通过 `@Transactional` 保证数据库自动回滚
- **Redis 清理**：`@AfterEach` 执行 `FLUSHDB` 清理 Redis 数据
- **单元测试**：纯 Mockito Mock，验证 Controller 层参数解析与异常翻译

## 测试类清单

### 1. FileServiceLifecycleIntegrationTest

全生命周期集成测试，验证文件服务完整业务流程。

| 测试用例 | 输入参数 | 预期结果 |
|---|---|---|
| 头像上传_成功 | PNG 图片(400x400) | 返回包含 avatar/USER_ID/profile.jpg 的 URL |
| 头像上传_非法类型 | PDF 文件 | 抛出 FILE_TYPE_NOT_ALLOWED (50002) |
| 头像上传_超大小 | 6MB 图片 | 抛出 FILE_TOO_LARGE (50001) |
| 上传图片_缩略图 | PNG 图片(800x600) | DB 记录生成，MinIO 存储 2 次，WS 推送 |
| 上传PDF_无缩略图 | PDF 文件 | DB 记录生成，MinIO 存储 1 次 |
| 分页查询 | roomId, page=1, size=10 | 按 uploadTime DESC 返回 |
| 生成下载链接_原子递增 | fileId | downloadCount +1，URL 正常返回 |
| 生成预览链接_缩略图优先 | fileId（含缩略图） | 返回缩略图 URL |
| 获取统计_Redis优先 | roomId | 返回 fileCount、totalSize |
| 获取统计_DB回退 | roomId（Redis 无值） | DB 计数并回填 Redis |
| 删除文件_本人 | fileId, uploaderId | DB 删除，MinIO 删除，Redis 配额释放 |
| 删除文件_权限拒绝 | fileId, strangerId | 抛出 FILE_ACCESS_DENIED (50005) |
| 清理房间_分批删除 | roomId（含多文件） | 分批删除，Redis 配额 key 清零 |
| 清理空房间 | roomId（无文件） | 快速返回 0 |
| 上传失败_非法类型 | .exe 文件 | 抛出 FILE_TYPE_NOT_ALLOWED |
| 上传失败_单文件超限 | 200MB 图片 | 抛出 FILE_TOO_LARGE |
| 上传失败_配额超限 | Redis INCRBY 返回 1.1GB | 抛出 ROOM_QUOTA_EXCEEDED |
| 上传失败_双重校验DB超限 | DB 已存 1.1GB 文件 | 抛出 ROOM_QUOTA_EXCEDED |
| 上传成功_配额小偏差 | DB=1023B，Redis=1025B | 正常通过（容差范围） |
| 文件不存在_查询 | fileId=999999 | 抛出 FILE_NOT_FOUND |
| 文件不存在_下载 | fileId=999998 | 抛出 FILE_NOT_FOUND |

### 2. FileServiceImplTest

Service 层单元测试，Mock 所有外部依赖。

| 测试用例 | 覆盖范围 |
|---|---|
| 头像上传_类型校验 | jpg/png/gif 格式，PDF 拒绝，MinIO 异常处理 |
| 文件上传_InputStream只读一次 | 流不可重复读验证，缩略图生成，配额预占 |
| 文件上传_配额超限拦截 | Redis INCRBY 原子预占超限，DB 双重校验 |
| 文件上传_异常回滚 | MinIO 失败时配额回滚，DB 记录回滚 |
| 分页查询_空结果 | 空集合边界条件 |
| 下载链接_原子递增 | SQL 原子递增，非 read-modify-write |
| 删除_权限校验 | 上传者/非上传者分支 |
| 删除_MinIO静默容错 | 原图/缩略图删除失败不影响 DB 删除 |
| 清理_分批与边界 | batchSize 边界，多批次退出条件 |
| 统计_Redis优先回退DB | Redis 有值/无值/异常 三种路径 |
| 清理_外部依赖容错 | MinIO/Redis 失败时仍执行核心删除 |

### 3. FileControllerUnitTest

Controller 层单元测试，Mock Service，验证 HTTP 响应封装。

| 测试用例 | 覆盖场景 |
|---|---|
| 头像上传_成功/失败 | URL 封装，异常翻译 |
| 文件上传_成功/失败 | FileVO 封装，配额异常 |
| 分页查询_自定义参数 | PageResult 封装 |
| 文件信息_存在/不存在 | FileVO 封装，404 异常 |
| 下载/预览_成功/失败 | URL 字符串封装，重定向 |
| 删除_成功/权限拒绝/不存在 | Boolean 封装，异常翻译 |
| 统计与清理_成功/空结果 | RoomFileStats 封装 |

## 运行方式

```bash
cd apps/backend/gopair-file-service
mvn test
```

## 依赖要求

- MySQL 8.0+，数据库名 `gopair_test`
- Redis 本地运行
- MinIO 存储（Mock，无需真实运行）
- RabbitMQ 连接（Mock，无需真实运行）
