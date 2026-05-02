package com.gopair.fileservice.service;

import com.gopair.fileservice.base.BaseIntegrationTest;
import com.gopair.fileservice.mapper.RoomFileMapper;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 文件服务集成测试支持类。
 *
 * * [核心策略]
 * - 继承 BaseIntegrationTest：获取 @Transactional 回滚 + Redis flushDb 清理 + FileServiceImpl 注入。
 * - MinIO/WebSocket/MQ 由 @MockBean 提供（定义在 BaseIntegrationTest 中）。
 * - Mapper 使用真实 Bean，通过 @Transactional 与真实 MySQL 交互。
 *
 * @author gopair
 */
public abstract class FileServiceIntegrationTestSupport extends BaseIntegrationTest {

    @Autowired
    protected RoomFileMapper roomFileMapper;
}
