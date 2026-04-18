-- ============================================================
-- GoPair Admin Service H2 测试数据库 Schema
-- 用于集成测试，H2 2.x MySQL 兼容模式
-- 注意：createTime/updateTime 由 MyBatis-Plus AutoFillMetaObjectHandler 填充，无需在 schema 中声明
-- ============================================================

-- 管理员账户表
CREATE TABLE IF NOT EXISTS admin_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(256) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    status INT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 管理员操作审计日志表
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT NOT NULL,
    admin_username VARCHAR(64) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    detail TEXT,
    ip_address VARCHAR(64),
    user_agent VARCHAR(256),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 用户表（USER 是 MySQL 保留字，H2 兼容模式下需双引号包裹）
CREATE TABLE IF NOT EXISTS "user" (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nickname VARCHAR(64),
    password VARCHAR(256),
    email VARCHAR(128),
    avatar VARCHAR(512),
    status CHAR(1) DEFAULT '0',
    remark VARCHAR(512),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 房间表
CREATE TABLE IF NOT EXISTS room (
    room_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_code VARCHAR(32) NOT NULL,
    room_name VARCHAR(128),
    description VARCHAR(512),
    max_members INT DEFAULT 10,
    current_members INT DEFAULT 0,
    owner_id BIGINT,
    status INT DEFAULT 0,
    expire_time TIMESTAMP,
    version INT DEFAULT 0,
    password_mode INT DEFAULT 0,
    password_hash VARCHAR(256),
    password_visible INT DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 房间成员表（继承 BaseEntity，含 create_time/update_time）
CREATE TABLE IF NOT EXISTS room_member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role INT DEFAULT 0,
    status INT DEFAULT 0,
    join_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 消息表
CREATE TABLE IF NOT EXISTS message (
    message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    sender_id BIGINT,
    message_type INT DEFAULT 1,
    content TEXT,
    file_url VARCHAR(512),
    file_name VARCHAR(256),
    file_size BIGINT,
    reply_to_id BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 房间文件表
CREATE TABLE IF NOT EXISTS room_file (
    file_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    uploader_nickname VARCHAR(64),
    file_name VARCHAR(256) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    thumbnail_path VARCHAR(512),
    file_size BIGINT DEFAULT 0,
    file_type VARCHAR(32),
    content_type VARCHAR(128),
    download_count INT DEFAULT 0,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 语音通话表
CREATE TABLE IF NOT EXISTS voice_call (
    call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    initiator_id BIGINT,
    call_type INT DEFAULT 1,
    status INT DEFAULT 0,
    start_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    end_time TIMESTAMP,
    duration INT DEFAULT 0,
    is_auto_created BOOLEAN DEFAULT FALSE
);

-- 语音通话参与者表
CREATE TABLE IF NOT EXISTS voice_call_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    join_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    leave_time TIMESTAMP,
    connection_status INT DEFAULT 0
);
