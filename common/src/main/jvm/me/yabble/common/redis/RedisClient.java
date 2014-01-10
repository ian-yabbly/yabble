package me.yabble.common.redis;

import com.google.common.base.Function;

import redis.clients.jedis.Jedis;

public interface RedisClient {
    <T> T work(Function<Jedis, T> f);

    <T> T workInLock(
            String lockKey,
            long maxWaitMs,
            boolean breakLock,
            Function<Jedis, T> f)
        throws LockAcquireFailedException;

    class LockAcquireFailedException extends RuntimeException {
        public LockAcquireFailedException(String message) {
            super(message);
        }
    }
}
