-- 参数说明：
-- KEYS[1] = 库存 key：seckill:stock:{voucherId}
-- KEYS[2] = 用户购买 set key：seckill:order:users:{voucherId}
-- ARGV[1] = userId，用于在用户购买set中判断是否已经购买
-- ARGV[2] = voucherId，用于获取获取库存，判断库存

-- 优化为Stream，替代BlockingQueue
-- KEYS[3] = stream.orders
-- ARGV[3] = orderId


-- 1. 判断库存是否充足
local stock = tonumber(redis.call('GET', KEYS[1]))
if stock < 1 then
    return 1 -- 库存不足
end

-- 2. 判断用户是否已经购买过
local isMember = redis.call('SISMEMBER',KEYS[2],ARGV[1]) -- ARGV[1] 是否存在于 KEYS[2] 集合中
if isMember == 1 then
    return 2 -- 用户已经购买过
end

-- 3. 扣件库存
redis.call('INCRBY', KEYS[1], -1)

-- 4. 将用户添加到购买 set 中, 表示用户已经购买
redis.call('SADD', KEYS[2], ARGV[1])

-- 5. 发送消息到Stream
redis.call('XADD', KEYS[3], '*',
    'orderId', ARGV[3],
    'userId', ARGV[1],
    'voucherId', ARGV[2])

-- 5. 返回 0 表示购买成功
return 0
