-- 消息表（IF NOT EXISTS 兼容测试隔离：H2 内存数据库在同 JVM 多测试间残留表结构）
CREATE TABLE IF NOT EXISTS message (
    message_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    message_type INT NOT NULL DEFAULT 1,
    content VARCHAR(2000),
    file_url VARCHAR(500),
    file_name VARCHAR(200),
    file_size BIGINT,
    reply_to_id BIGINT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_message_room_id ON message(room_id);
CREATE INDEX IF NOT EXISTS idx_message_sender_id ON message(sender_id);
CREATE INDEX IF NOT EXISTS idx_message_room_create_time ON message(room_id, create_time);

-- 用户表（Mapper JOIN 查询昵称/头像，兼容 H2 原生模式）
CREATE TABLE IF NOT EXISTS app_user (
    user_id BIGINT PRIMARY KEY,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    username VARCHAR(100),
    password VARCHAR(200),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
