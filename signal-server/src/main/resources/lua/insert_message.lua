-- insert a message to a message queue

local queueKey               = KEYS[1] -- sorted set of messages
local queueMetadataKey       = KEYS[2] -- map of message GUID to messageId
local queueTotalIndexKey     = KEYS[3] -- sorted set of all queues, by timestamp of oldest message
local message                = ARGV[1] -- [bytes] the message to insert
local currentTime            = ARGV[2] -- [number] the message timestamp, to sort the queue in the queueTotalIndexKey
local guid                   = ARGV[3] -- [string] the GUID of the message

-- check to see if message with given GUID was already in the map, if so return the corresponding messageId
if redis.call("HEXISTS", queueMetadataKey, guid) == 1 then
    return tonumber(redis.call("HGET", queueMetadataKey, guid))
end

-- calculate the next messageId by adding 1 to the counter
local messageId = redis.call("HINCRBY", queueMetadataKey, "counter", 1)

-- add the message (if not yet added) to the queueKey sorted on messageId.
redis.call("ZADD", queueKey, "NX", messageId, message)
redis.call("EXPIRE", queueKey, 3974400) -- 46 * 24 * 60 * 60 = 46 days

-- add a mapping from message GUID to messagedId
redis.call("HSET", queueMetadataKey, guid, messageId)
redis.call("EXPIRE", queueMetadataKey, 3974400) -- 46 * 24 * 60 * 60 = 46 days

-- insert the queueKey in the queueTotalIndexKey (if not already added), sorted on timestamp
redis.call("ZADD", queueTotalIndexKey, "NX", currentTime, queueKey)

return true
