-- KEYS[1] = room meta key: room:{roomId}:meta
-- KEYS[2] = room members set key: room:{roomId}:members
-- KEYS[3] = room pending hash/set key: room:{roomId}:pending
-- KEYS[4] = join token key: join:{token}
-- ARGV[1] = userId
-- ARGV[2] = displayName (unused in Lua, for reference)
-- ARGV[3] = token
-- ARGV[4] = now (ms)
-- ARGV[5] = join token ttl (seconds)
-- Return codes:
-- 0 = ACCEPTED
-- 1 = ALREADY_JOINED
-- 2 = FULL
-- 3 = CLOSED
-- 4 = EXPIRED
-- 5 = PROCESSING

local metaKey = KEYS[1]
local membersKey = KEYS[2]
local pendingKey = KEYS[3]
local tokenKey = KEYS[4]
local userId = ARGV[1]
local token = ARGV[3]
local now = tonumber(ARGV[4])
local tokenTtl = tonumber(ARGV[5])

-- status: 0 active, 1 closed
local status = redis.call('HGET', metaKey, 'status')
if status and tonumber(status) ~= 0 then
  return 3
end

local expireAt = redis.call('HGET', metaKey, 'expireAt')
if expireAt and tonumber(expireAt) > 0 and now > tonumber(expireAt) then
  return 4
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