-- 语音通话表
CREATE TABLE IF NOT EXISTS voice_call (
    call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    initiator_id BIGINT NOT NULL,
    call_type INT,
    status INT,
    start_time TIMESTAMP NULL DEFAULT NULL,
    end_time TIMESTAMP NULL DEFAULT NULL,
    duration INT,
    is_auto_created BOOLEAN,
    INDEX idx_voice_call_room_status (room_id, status),
    INDEX idx_voice_call_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 语音通话参与者表
CREATE TABLE IF NOT EXISTS voice_call_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    join_time TIMESTAMP NULL DEFAULT NULL,
    leave_time TIMESTAMP NULL DEFAULT NULL,
    connection_status INT,
    UNIQUE KEY uk_participant_call_user (call_id, user_id),
    INDEX idx_participant_call_active (call_id, leave_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
