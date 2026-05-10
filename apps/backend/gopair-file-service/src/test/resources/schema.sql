-- 房间文件表
CREATE TABLE IF NOT EXISTS room_file (
    file_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    uploader_nickname VARCHAR(100),
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    thumbnail_size BIGINT NOT NULL DEFAULT 0,
    file_type VARCHAR(50) NOT NULL,
    content_type VARCHAR(100),
    download_count INT NOT NULL DEFAULT 0,
    upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    message_id BIGINT NULL DEFAULT NULL,
    INDEX idx_room_id (room_id),
    INDEX idx_uploader_id (uploader_id),
    INDEX idx_upload_time (upload_time),
    INDEX idx_message_id (message_id)
);
