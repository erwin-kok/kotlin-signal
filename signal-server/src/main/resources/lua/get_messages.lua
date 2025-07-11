-- get items from a message queue

local queueKey          = KEYS[1] -- sorted set of all messages
local limit             = ARGV[1] -- [number] the maximum number of messages to return
local afterMessageId    = ARGV[2] -- [number] messageId to start after

-- afterMessageId must be provided
if afterMessageId == "null" or afterMessageId == nil then
    return redis.error_reply("ERR afterMessageId is required")
end

-- return messages after afterMessageId with limit and scores
return redis.call("ZRANGE", queueKey, "("..afterMessageId, "+inf", "BYSCORE", "LIMIT", 0, limit, "WITHSCORES")
