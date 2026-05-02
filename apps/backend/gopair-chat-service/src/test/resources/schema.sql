-- 好友关系表
CREATE TABLE IF NOT EXISTS friend (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL COMMENT '用户ID（较小的一方）',
    friend_id BIGINT NOT NULL COMMENT '好友ID（较大的一方）',
    remark VARCHAR(50) DEFAULT NULL COMMENT '好友备注',
    status CHAR(1) NOT NULL DEFAULT '0' COMMENT '0-待确认 1-已同意 2-已拒绝',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_friend (user_id, friend_id),
    INDEX idx_friend_id (friend_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';

-- 好友申请表
CREATE TABLE IF NOT EXISTS friend_request (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_user_id BIGINT NOT NULL COMMENT '申请人用户ID',
    to_user_id BIGINT NOT NULL COMMENT '被申请人用户ID',
    status CHAR(1) NOT NULL DEFAULT '0' COMMENT '0-待处理 1-已同意 2-已拒绝',
    message VARCHAR(100) DEFAULT NULL COMMENT '申请附言',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_request (from_user_id, to_user_id),
    INDEX idx_to_user (to_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请表';

-- 私聊消息表
CREATE TABLE IF NOT EXISTS private_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL COMMENT '会话ID：min*1e9+max',
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    message_type INT NOT NULL DEFAULT 1 COMMENT '1-文本 2-图片 3-文件',
    content TEXT DEFAULT NULL COMMENT '文本内容',
    file_url VARCHAR(512) DEFAULT NULL COMMENT '文件URL',
    file_name VARCHAR(255) DEFAULT NULL COMMENT '文件名',
    file_size BIGINT DEFAULT NULL COMMENT '文件大小',
    is_recalled TINYINT(1) NOT NULL DEFAULT 0,
    recalled_at DATETIME DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id),
    INDEX idx_receiver_unread (receiver_id, is_recalled),
    INDEX idx_created_at (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='私聊消息表';

-- 用户表（只读，用于 JOIN 查询昵称/头像）
CREATE TABLE IF NOT EXISTS app_user (
    user_id BIGINT PRIMARY KEY,
    nickname VARCHAR(100),
    avatar VARCHAR(500),
    username VARCHAR(100),
    password VARCHAR(200),
    email VARCHAR(100),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表（测试用）';
