-- =====================================================
-- GoPair 房间服务数据库迁移脚本
-- 目的：删除匿名用户支持和聊天功能
-- 日期：2025-01-15
-- =====================================================

-- 1. 数据备份
-- 备份原始表数据（执行前请确保有完整的数据库备份）
CREATE TABLE IF NOT EXISTS room_backup AS SELECT * FROM room;
CREATE TABLE IF NOT EXISTS room_member_backup AS SELECT * FROM room_member;
CREATE TABLE IF NOT EXISTS chat_message_backup AS SELECT * FROM chat_message;

-- 2. 删除聊天消息表
-- 完全移除聊天功能
DROP TABLE IF EXISTS chat_message;

-- 3. 清理匿名用户数据
-- 删除所有匿名用户的房间成员记录
DELETE FROM room_member WHERE user_id IS NULL;

-- 删除由匿名用户创建的房间
DELETE FROM room WHERE owner_id IS NULL;

-- 4. 修改 room 表结构
-- 删除匿名用户相关字段
ALTER TABLE room 
DROP COLUMN IF EXISTS owner_nickname,
DROP COLUMN IF EXISTS allow_anonymous;

-- 确保 owner_id 不能为空
ALTER TABLE room 
MODIFY COLUMN owner_id BIGINT NOT NULL COMMENT '房主用户ID（必须为注册用户）';

-- 5. 修改 room_member 表结构
-- 删除匿名用户ID字段
ALTER TABLE room_member 
DROP COLUMN IF EXISTS anonymous_id;

-- 重命名昵称字段，明确语义
ALTER TABLE room_member 
CHANGE COLUMN nickname display_name VARCHAR(50) NOT NULL COMMENT '房间内显示名称';

-- 确保 user_id 不能为空
ALTER TABLE room_member 
MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户ID（必须为注册用户）';

-- 6. 重建索引
-- 删除匿名用户相关的唯一索引
ALTER TABLE room_member DROP INDEX IF EXISTS uk_room_anonymous;

-- 确保房间-用户的唯一性约束
ALTER TABLE room_member 
ADD UNIQUE INDEX uk_room_user (room_id, user_id);

-- 7. 验证数据完整性
-- 检查是否还有孤儿记录
SELECT 'Orphaned room_member records:' as check_type, COUNT(*) as count
FROM room_member rm 
LEFT JOIN room r ON rm.room_id = r.room_id 
WHERE r.room_id IS NULL;

-- 检查房间表数据
SELECT 'Rooms without valid owner:' as check_type, COUNT(*) as count
FROM room 
WHERE owner_id IS NULL;

-- 检查房间成员表数据
SELECT 'Room members without valid user:' as check_type, COUNT(*) as count
FROM room_member 
WHERE user_id IS NULL;

-- 显示迁移后的统计信息
SELECT 'Total rooms:' as info_type, COUNT(*) as count FROM room;
SELECT 'Total room members:' as info_type, COUNT(*) as count FROM room_member;

-- =====================================================
-- 迁移完成
-- 注意：执行此脚本前请确保：
-- 1. 已备份完整数据库
-- 2. 已停止相关应用服务
-- 3. 已通知用户系统维护
-- ===================================================== 