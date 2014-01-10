package me.yabble.common.redis;

import com.google.common.base.Function;

import org.joda.time.DateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;

public class RedisClientImpl implements RedisClient {
    private static final Logger log = LoggerFactory.getLogger(RedisClientImpl.class);

    private long maxConnectionPoolTimeoutMs = 0l;
    private int maxActiveConnections;
    private int db;
    private JedisPool jedisPool;

    public RedisClientImpl(int maxActiveConnections, JedisPool jedisPool, int db) {
        this.maxActiveConnections = maxActiveConnections;
        this.jedisPool = jedisPool;
        this.db = db;
    }

    @Override
    public <T> T workInLock(
            final String lockKey,
            final long maxWaitMs,
            final boolean breakLock,
            final Function<Jedis, T> f)
        throws LockAcquireFailedException
    {
        return work(new Function<Jedis, T>() {
            @Override
            public T apply(Jedis jedis) {
                // Get a lock
                long sleepTime = 200l;

                long start = System.currentTimeMillis();
                while (1l != jedis.setnx(lockKey, DateTime.now().toString())) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) { throw new RuntimeException(e); }

                    if ((System.currentTimeMillis() - start) > maxWaitMs) {
                        if (breakLock) {
                            try {
                                throw new RuntimeException();
                            } catch (RuntimeException e) {
                                log.error("Breaking lock [{}] because max wait time has been exceeded [{}ms]", new Object[] {lockKey, maxWaitMs}, e);
                            }
                            jedis.set(lockKey, DateTime.now().toString());
                            break;
                        } else {
                            throw new LockAcquireFailedException(String.format("Max wait time exceeded [%dms]", maxWaitMs));
                        }
                    }
                }

                try {
                    return f.apply(jedis);
                } finally {
                    jedis.del(lockKey);
                }
            }
        });
    }

    /*
     * Handle timed out connections. Also must handle redis being down.
     * If redis is down, an exponential backoff will be employed if
     * maxConnectionPoolTimeoutMs is gt 0l.
     */
    @Override
    public <T> T work(Function<Jedis, T> f) {
        int tryCountdown = maxActiveConnections;
        long sleepTime = 10l;
        while (true) {
            try {
                return doWork(f);
            } catch (JedisConnectionException e) {
                if (tryCountdown < 0) {
                    if (sleepTime > maxConnectionPoolTimeoutMs) {
                        log.info("Reached max sleep time [{}]. Will not wait any longer", sleepTime);
                        throw e;
                    }
                    try {
                        log.info("Sleeping for [{}ms]", sleepTime);
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e2) {
                        throw new RuntimeException(e2);
                    }
                    sleepTime *= 2;
                }
            }
            tryCountdown--;
        }
    }

    private <T> T doWork(Function<Jedis, T> f) {
        Jedis jedis = jedisPool.getResource();

        boolean isBroken = false;
        try {
            jedis.select(db);
            return f.apply(jedis);
        } catch (JedisConnectionException e) {
            log.debug("Caught JedisConnectionException");
            isBroken = true;
            throw e;
        } catch (Exception e) { // Check if the Function wrapped a JedisConnectionException
            if (e.getCause() != null && e.getCause() instanceof JedisConnectionException) {
                log.debug("Caught wrapped JedisConnectionException");
                isBroken = true;
                throw (JedisConnectionException) e.getCause();
            }
            // TODO double check that this is correct
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            log.trace("Jedis isBroken [{}]", isBroken);
            if (jedis != null) {
                if (isBroken) {
                    log.debug("Returned broken connection");
                    jedisPool.returnBrokenResource(jedis);
                } else {
                    jedisPool.returnResource(jedis);
                }
            }
        }
    }

    public void setMaxConnectionPoolTimeoutMs(long maxConnectionPoolTimeoutMs) {
        this.maxConnectionPoolTimeoutMs = maxConnectionPoolTimeoutMs;
    }

    public void setMaxActiveConnections(int maxActiveConnections) {
        this.maxActiveConnections = maxActiveConnections;
    }
}
