package io.seata.server.storage.redis.lock;

import io.seata.server.storage.redis.JedisPooledFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.params.SetParams;

/**
 * @description: redis 分布式锁
 * @author: zhongxiang.wang
 * @date: 2021-03-02 11:34
 */
public class RedisDistributedLocker {

    protected static final Logger LOGGER = LoggerFactory.getLogger(RedisDistributedLocker.class);
    private static final String SUCCESS = "OK";

    /**
     * Acquire the distributed lock
     *
     * @param lockKey    the lock key
     * @param lockValue  the lock value
     * @param expireTime the expireTime,to prevent the dead lock when current TC who acquired the lock has down
     * @return
     */
    public static boolean acquireScheduledLock(String lockKey, String lockValue, Integer expireTime) {
        try (Jedis jedis = JedisPooledFactory.getJedisInstance()) {
            //Don't need retry,if can't acquire the lock,let the other get the lock
            String result = jedis.set(lockKey, lockValue, SetParams.setParams().nx().ex(expireTime));
            if (SUCCESS.equalsIgnoreCase(result)) {
                return true;
            }
            return false;
        } catch (Exception ex) {
            LOGGER.warn("The {} acquired the {} distributed lock failed.", lockValue, lockKey, ex);
            return false;
        }
    }

    /**
     * Release the distributed lock
     *
     * @param lockKey   the lock key
     * @param lockValue the lock value
     * @return
     */
    public static boolean releaseScheduleLock(String lockKey, String lockValue) {
        try (Jedis jedis = JedisPooledFactory.getJedisInstance()) {
            jedis.watch(lockKey);
            //Check the value to prevent release the other's lock
            if (lockValue.equals(jedis.get(lockKey))) {
                Transaction multi = jedis.multi();
                multi.del(lockKey);
                multi.exec();
                return true;
            }
            //If other one get the lock,we release lock success too
            jedis.unwatch();
            return true;
        } catch (Exception ex) {
            LOGGER.warn("The {} release the {} distributed lock failed.", lockValue, lockKey, ex);
            //TODO ?返回true等待自然过期还是不断重试？重试到自然过期的时间然后结束？重试就需要时间比对增加了复杂性
            return true;
        }
    }

}
