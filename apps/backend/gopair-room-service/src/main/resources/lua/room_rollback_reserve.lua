-- KEYS[1] = room meta key: room:{roomId}:meta
-- KEYS[2] = room pending hash key: room:{roomId}:pending
-- ARGV[1] = userId
-- ARGV[2] = tokenKey (join:{token})
-- Return: 1 on success, 0 otherwise

local metaKey = KEYS[1]
local pendingKey = KEYS[2]
local userId = ARGV[1]
local tokenKey = ARGV[2]

-- if pending exists for user, remove it
if redis.call('HEXISTS', pendingKey, userId) == 1 then
  redis.call('HDEL', pendingKey, userId)
end

-- decrement reserved if > 0
local reserved = tonumber(redis.call('HGET', metaKey, 'reserved') or '0')
if reserved > 0 then
  redis.call('HINCRBY', metaKey, 'reserved', -1)
end

-- mark token failed if exists
if tokenKey and tokenKey ~= '' then
  redis.call('SET', tokenKey, 'FAILED')
end

return 1 