-- KEYS[1] = room meta key: room:{roomId}:meta
-- KEYS[2] = room members set key: room:{roomId}:members
-- KEYS[3] = room pending removal hash key: room:{roomId}:pending_removal
-- ARGV[1] = userId
-- Return: 1 on success, 0 = nothing to rollback

local metaKey = KEYS[1]
local membersKey = KEYS[2]
local pendingRemovalKey = KEYS[3]
local userId = ARGV[1]

-- 若 pending removal 不存在，说明已被 Consumer 处理，无需回滚
if redis.call('HEXISTS', pendingRemovalKey, userId) == 0 then
  return 0
end

-- 回滚：恢复成员 + confirmed++
redis.call('SADD', membersKey, userId)
redis.call('HINCRBY', metaKey, 'confirmed', 1)
redis.call('HDEL', pendingRemovalKey, userId)

return 1
