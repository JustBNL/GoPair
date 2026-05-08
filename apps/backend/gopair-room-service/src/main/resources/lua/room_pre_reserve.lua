-- KEYS[1] = room meta key: room:{roomId}:meta
-- KEYS[2] = room members set key: room:{roomId}:members
-- KEYS[3] = room pending hash/set key: room:{roomId}:pending
-- KEYS[4] = join token key: join:{token}
-- ARGV[1] = userId
-- ARGV[2] = token
-- ARGV[3] = now (ms)
-- ARGV[4] = join token ttl (seconds)
-- Return codes:
-- 0 = ACCEPTED
-- 1 = ALREADY_JOINED
-- 2 = FULL
-- 3 = ROOM_NOT_ACTIVE (closed or archived)
-- 4 = EXPIRED (expireAt 已过但 status 仍为 0 的边缘情况，由 Java 侧定期修正)
-- 5 = ALREADY_PROCESSING

local metaKey = KEYS[1]
local membersKey = KEYS[2]
local pendingKey = KEYS[3]
local tokenKey = KEYS[4]
local userId = ARGV[1]
local token = ARGV[2]
local now = tonumber(ARGV[3])
local tokenTtl = tonumber(ARGV[4])

-- EXPIRED(2) 允许进入只读模式，CLOSED(1) 和 ARCHIVED(3) 拒绝
local status = redis.call('HGET', metaKey, 'status')
if status then
  local s = tonumber(status)
  if s == 1 or s == 3 then
    return 3
  end
end

-- already joined
if redis.call('SISMEMBER', membersKey, userId) == 1 then
  return 1
end

-- processing
if redis.call('HEXISTS', pendingKey, userId) == 1 then
  return 5
end

local max = tonumber(redis.call('HGET', metaKey, 'max') or '0')
local confirmed = tonumber(redis.call('HGET', metaKey, 'confirmed') or '0')
local reserved = tonumber(redis.call('HGET', metaKey, 'reserved') or '0')

if confirmed + reserved >= max then
  return 2
end

-- reserve slot and mark pending
redis.call('HINCRBY', metaKey, 'reserved', 1)
redis.call('HSET', pendingKey, userId, token)
-- set token info stub to allow query; value could be enriched by Java when set if needed
redis.call('SETEX', tokenKey, tokenTtl, 'PROCESSING')

return 0
