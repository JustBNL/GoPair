-- KEYS[1] = room meta key: room:{roomId}:meta
-- KEYS[2] = room members set key: room:{roomId}:members
-- KEYS[3] = room pending removal hash key: room:{roomId}:pending_removal
-- ARGV[1] = userId
-- ARGV[2] = leaveType (1=主动离开 2=被踢 3=房间关闭被动离开)
-- ARGV[3] = pending TTL seconds (防止键泄漏)
-- Return codes:
-- 0 = 成功 pending
-- 1 = 不在房间

local metaKey = KEYS[1]
local membersKey = KEYS[2]
local pendingRemovalKey = KEYS[3]
local userId = ARGV[1]
local leaveType = ARGV[2]
local ttl = tonumber(ARGV[3])

-- 检查用户是否在房间成员列表中
if redis.call('SISMEMBER', membersKey, userId) == 0 then
  return 1
end

-- 原子执行：移除成员 + confirmed-- + 写入 pending removal
redis.call('SREM', membersKey, userId)
redis.call('HINCRBY', metaKey, 'confirmed', -1)
redis.call('HSET', pendingRemovalKey, userId, leaveType)
redis.call('EXPIRE', pendingRemovalKey, ttl)

return 0
