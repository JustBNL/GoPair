-- 语音通话表
CREATE TABLE IF NOT EXISTS voice_call (
    call_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    initiator_id BIGINT NOT NULL,
    call_type INT,
    status INT,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    duration INT,
    is_auto_created BOOLEAN
);

-- 语音通话参与者表
CREATE TABLE IF NOT EXISTS voice_call_participant (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    call_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    join_time TIMESTAMP,
    leave_time TIMESTAMP,
    connection_status INT
);
