local messageQueueKey               = KEYS[1] -- sorted set of messages
local messageQueueMetadataKey       = KEYS[2] -- map of message GUID to messageId
local queueIndexKey                 = KEYS[3] -- sorted set of all queues, by timestamp of oldest message
local message                       = ARGV[1] -- [bytes] the message to insert
local currentTime                   = ARGV[2] -- [number] the message timestamp, to sort the queue in the queueIndexKey
local guid                          = ARGV[3] -- [string] the GUID of the message

-- check to see if message with given GUID was already in the map, if so return the corresponding messageId
if redis.call("HEXISTS", messageQueueMetadataKey, guid) == 1 then
    return tonumber(redis.call("HGET", messageQueueMetadataKey, guid))
end

-- calculate the next messageId by adding 1 to the counter
local messageId = redis.call("HINCRBY", messageQueueMetadataKey, "counter", 1)

-- add the message (if not yet added) to the messageQueueKey sorted on messageId.
redis.call("ZADD", messageQueueKey, "NX", messageId, message)
redis.call("EXPIRE", messageQueueKey, 3974400) -- 46 * 24 * 60 * 60 = 46 days

-- add a mapping from message GUID to messagedId
redis.call("HSET", messageQueueMetadataKey, guid, messageId)
redis.call("EXPIRE", messageQueueMetadataKey, 3974400) -- 46 * 24 * 60 * 60 = 46 days

-- insert the messageQueueKey in the queueIndexKey (if not already added), sorted on timestamp
redis.call("ZADD", queueIndexKey, "NX", currentTime, messageQueueKey)

return true
