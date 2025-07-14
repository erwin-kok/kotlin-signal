-- remove messages from a message queue by guid

local messageQueueKey               = KEYS[1] -- sorted set of messages
local messageQueueMetadataKey       = KEYS[2] -- map of message GUID to messageId
local queueIndexKey                 = KEYS[3] -- sorted set of all queues, by timestamp of oldest message
local messageGuids                  = ARGV    -- [list[string]] message GUIDs

local removedMessages = {}

-- Iterate over all messageGuids
for _, guid in ipairs(messageGuids) do
    -- Get messageId of given guid
    local messageId = redis.call("HGET", messageQueueMetadataKey, guid)

    -- If messageId exists...
    if messageId then
        -- Get message
        local message = redis.call("ZRANGE", messageQueueKey, messageId, messageId, "BYSCORE", "LIMIT", 0, 1)

        -- Remove messageId from queue
        redis.call("ZREMRANGEBYSCORE", messageQueueKey, messageId, messageId)

        -- Remove guid -- messageId mapping
        redis.call("HDEL", messageQueueMetadataKey, guid)

        -- Insert message in removedMessages
        if message and next(message) then
            table.insert(removedMessages, message[1])
        end
    end
end

-- messageQueueKey is empty, remove all metadata
if (redis.call("ZCARD", messageQueueKey) == 0) then
    redis.call("DEL", messageQueueKey)
    redis.call("DEL", messageQueueMetadataKey)
    redis.call("ZREM", queueIndexKey, messageQueueKey)
end

return removedMessages
