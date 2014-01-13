package me.yabble.common.wq;

import me.yabble.common.redis.RedisClient;
import me.yabble.common.proto.CommonProtos.DelayedJob;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.joda.time.DateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.Lifecycle;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static me.yabble.common.SecurityUtils.base64Encode;
import static me.yabble.common.SecurityUtils.utf8Encode;

public class RedisWorkQueue implements Lifecycle, Runnable, WorkQueue {
    private static final Logger log = LoggerFactory.getLogger(RedisWorkQueue.class);

    private static final String QUEUE_PREFIX = "queue:";

    private RedisClient client;
    private Gson gson = new Gson();
    private ScheduledExecutorService executorService;
    private boolean isRunning = false;

    public RedisWorkQueue(RedisClient redisClient) {
        client = redisClient;
    }

    @Override
    public void submit(final String qname, final byte[] value, long delay, TimeUnit delayUnit) {
        // HACK But I think it'll be okay
        if (!"prod".equals(System.getProperty("app.env"))) {
            submit(qname, value);
        } else {
            final DateTime d = DateTime.now().plusMillis((int) delayUnit.toMillis(delay));

            client.work(new Function<Jedis, Long>() {
                public Long apply(Jedis jedis) {
                    long itemId = createItem(jedis, qname, value);
                    long djid = jedis.incr(seqName("delayed-jobs"));

                    DelayedJob dj = DelayedJob.newBuilder()
                            .setId(djid)
                            .setQname(qname)
                            .setItemId(itemId)
                            .setDatetimeToSubmit(d.toString())
                            .build();

                    byte[] bytes = dj.toByteArray();

                    jedis.hset(u("delayed-jobs"), u(djid), dj.toByteArray());

                    jedis.zadd(u("delayed-job-ids"), d.getMillis(), u(djid));

                    return djid;
                }
            });
        }
    }

    @Override
    public long submit(final String qname, final long itemId) {
        client.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                jedis.rpush(pendingName(qname), String.valueOf(itemId));
                return null;
            }
        });

        return itemId;
    }

    @Override
    public void submit(final String qname, final byte[] value) {
        try {
            client.work(new Function<Jedis, Long>() {
                public Long apply(Jedis jedis) {
                    long id = createItem(jedis, qname, value);
                    jedis.rpush(pendingName(qname), String.valueOf(id));
                    return id;
                }
            });
        } catch (JedisConnectionException e) {
            // TODO This is not right
            log.error("Could not submit job to queue [{}]", e.getMessage());
            //return 0l;
        }
    }

    @Override
    public Optional<WorkQueue.Item> begin(
            final String qname, final long timeout, final TimeUnit unit)
    {
        return Optional.fromNullable(client.work(new Function<Jedis, WorkQueue.Item>() {
            public WorkQueue.Item apply(Jedis jedis) {
                String val = jedis.lpop(pendingName(qname));
                Item item = null;
                if (val != null) {
                    // Put this in the in-progress list
                    item = findItem(jedis, qname, val);
                    item.setLastInProgressDate(new Date());
                    item.setTimeout(unit.toMillis(timeout));
                    updateItem(jedis, qname, item);
                    jedis.rpush(inProgressName(qname), val);
                }
                return item;
            }
        }));
    }

    @Override
    public Optional<WorkQueue.Item> begin(
            final String qname,
            final long timeout, final TimeUnit unit,
            final long waitTimeout, final TimeUnit waitUnit)
    {
        return Optional.fromNullable(client.work(new Function<Jedis, WorkQueue.Item>() {
            public WorkQueue.Item apply(Jedis jedis) {
                List<String> vals =
                        jedis.blpop((int) waitUnit.toSeconds(waitTimeout), pendingName(qname));
                Item item = null;
                if (vals != null && !vals.isEmpty()) {
                    assert(vals.size() == 2);
                    String idStr = vals.get(1);
                    // Put this in the in-progress list

                    try {
                        item = findItem(jedis, qname, idStr);
                    } catch (ItemNotFoundException e) {
                        log.error(e.getMessage(), e);
                        delete(qname, Long.parseLong(idStr));
                        return null;
                    }

                    item.setLastInProgressDate(new Date());
                    item.setTimeout(unit.toMillis(timeout));
                    updateItem(jedis, qname, item);
                    jedis.rpush(inProgressName(qname), idStr);
                }
                return item;
            }
        }));
    }

    @Override
    public void commit(final String qname, final long id) {
        final String ipName = inProgressName(qname);

        Long count = client.work(new Function<Jedis, Long>() {
            public Long apply(Jedis jedis) {
                deleteItem(jedis, qname, id);
                return jedis.lrem(inProgressName(qname), 0, String.valueOf(id));
            }
        });

        if (count == null) {
            throw new RuntimeException(
                    String.format("Received null count after lrem on [%s]", ipName));
        }

        if (count == 0) {
            log.error("Item not found for removal from in-progress queue [{}]", ipName);
        }

        if (count > 1) {
            throw new RuntimeException(String.format(
                    "More than 1 item removed from in-progress queue [%s] [%d]",
                    ipName, count));
        }
    }

    @Override
    public void rollback(final String qname, final long id, final String message) {
        client.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                jedis.lrem(inProgressName(qname), 0, String.valueOf(id));
                jedis.rpush(pendingName(qname), String.valueOf(id));

                Item item = findItem(jedis, qname, id);
                item.attemptCount++;
                item.addFailureMessage(message);
                updateItem(jedis, qname, item);

                return null;
            }
        });
    }

    @Override
    public void fail(final String qname, final long id, final String message) {
        client.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                Item item = findItem(jedis, qname, id);
                item.attemptCount++;
                item.addFailureMessage(message);
                updateItem(jedis, qname, item);

                jedis.lrem(inProgressName(qname), 0, String.valueOf(id));
                jedis.rpush(failName(qname), String.valueOf(id));

                return null;
            }
        });
    }

    @Override
    public void retry(final String qname, final long id) {
        client.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                jedis.lrem(failName(qname), 0, String.valueOf(id));
                jedis.rpush(pendingName(qname), String.valueOf(id));
                return null;
            }
        });
    }

    @Override
    public void delete(final String qname) {
        client.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                jedis.del(qName(qname));
                jedis.del(inProgressName(qname));
                jedis.del(seqName(qname));
                jedis.del(failName(qname));
                jedis.del(pendingName(qname));
                return null;
            }
        });
    }

    @Override
    public boolean delete(final String qname, final long id) {
        return client.work(new Function<Jedis, Boolean>() {
            public Boolean apply(Jedis jedis) {
                jedis.lrem(inProgressName(qname), 0, String.valueOf(id));
                jedis.lrem(failName(qname), 0, String.valueOf(id));
                jedis.lrem(pendingName(qname), 0, String.valueOf(id));
                return deleteItem(jedis, qname, id);
            }
        });
    }

    @Override
    public long size(final String qname) {
        return client.work(new Function<Jedis, Long>() {
            public Long apply(Jedis jedis) {
                return jedis.llen(pendingName(qname));
            }
        }).longValue();
    }

    @Override
    public long inProgressSize(final String qname) {
        return client.work(new Function<Jedis, Long>() {
            public Long apply(Jedis jedis) {
                return jedis.llen(inProgressName(qname));
            }
        }).longValue();
    }

    @Override
    public long failSize(final String qname) {
        return client.work(new Function<Jedis, Long>() {
            public Long apply(Jedis jedis) {
                return jedis.llen(failName(qname));
            }
        }).longValue();
    }

    @Override
    public List<WorkQueue.Item> getItems(final String qname, final int offset, final int limit) {
        return client.work(new Function<Jedis, List<WorkQueue.Item>>() {
            public List<WorkQueue.Item> apply(Jedis jedis) {
                List<String> ids = jedis.lrange(pendingName(qname), offset, (limit-1));
                List<WorkQueue.Item> items = new ArrayList<WorkQueue.Item>();
                if (ids != null) {
                    for (String idStr : ids) {
                        items.add(findItem(jedis, qname, idStr));
                    }
                }
                return items;
            }
        });
    }

    @Override
    public List<WorkQueue.Item> getInProgressItems(
            final String qname, final int offset, final int limit)
    {
        return client.work(new Function<Jedis, List<WorkQueue.Item>>() {
            public List<WorkQueue.Item> apply(Jedis jedis) {
                List<String> ids = jedis.lrange(inProgressName(qname), offset, (limit-1));
                List<WorkQueue.Item> items = new ArrayList<WorkQueue.Item>();
                if (ids != null) {
                    for (String idStr : ids) {
                        items.add(findItem(jedis, qname, idStr));
                    }
                }
                return items;
            }
        });
    }

    @Override
    public List<WorkQueue.Item> getFailedItems(
            final String qname, final int offset, final int limit)
    {
        return client.work(new Function<Jedis, List<WorkQueue.Item>>() {
            public List<WorkQueue.Item> apply(Jedis jedis) {
                List<String> ids = jedis.lrange(failName(qname), offset, (limit-1));
                List<WorkQueue.Item> items = new ArrayList<WorkQueue.Item>();
                if (ids != null) {
                    for (String idStr : ids) {
                        items.add(findItem(jedis, qname, idStr));
                    }
                }
                return items;
            }
        });
    }

    @Override
    public List<DelayedJob> findAllDelayedJobs(final int offset, final int limit) {
        return client.work(new Function<Jedis, List<DelayedJob>>() {
            public List<DelayedJob> apply(Jedis jedis) {
                Set<String> vals = jedis.zrange("delayed-job-ids", offset, limit);
                List<DelayedJob> djs = Lists.newArrayList();
                for (String s : vals) {
                    try {
                        byte[] val = jedis.hget(u("delayed-jobs"), u(s));
                        djs.add(DelayedJob.parseFrom(val));
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
                return djs;
            }
        });
    }

    @Override
    public void undelayAllJobs() {
        int offset = 0;
        int limit = 256;

        List<DelayedJob> djs = findAllDelayedJobs(0, limit);
        while (djs.size() > 0) {
            for (DelayedJob j : djs) {
                undelayJob(j.getId());
            }
            offset += limit;
            djs = findAllDelayedJobs(0, limit);
        }
    }

    @Override
    public void undelayJob(final long id) {
        DelayedJob dj = client.work(new Function<Jedis, DelayedJob>() {
            @Override
            public DelayedJob apply(Jedis jedis) {
                try {
                    byte[] v = jedis.hget(u("delayed-jobs"), u(String.valueOf(id)));
                    return DelayedJob.parseFrom(v);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });

        // TODO this should be done within a txn
        deleteDelayedJob(id);
        submit(dj.getQname(), dj.getItemId());
    }

    @Override
    public List<String> getAllQueueNames() {
        Set<String> longQNames = client.work(new Function<Jedis, Set<String>>() {
            public Set<String> apply(Jedis jedis) {
                return jedis.keys("__queue:*-seq");
            }
        });
        List<String> qnames = new ArrayList<String>();
        for (String n : longQNames) {
            qnames.add(n.substring(8, n.length() - 4));
        }
        return qnames;
    }

    // Lifecycle methods
    @Override
    public void start() {
        executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                return new Thread(r, "delayed-job-worker");
            }
        });
        executorService.scheduleWithFixedDelay(this, 10l, 10l, TimeUnit.SECONDS);
        isRunning = true;
    }

    @Override
    public void stop() {
        executorService.shutdown();
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }
    // END Lifecycle methods

    @Override
    public void run() {
        client.workInLock("delayed-job.lock", 4000l, true, new Function<Jedis, Void>() {
            @Override
            public Void apply(Jedis jedis) {
                int offset = 0;
                int limit = 32;

                DateTime now = DateTime.now();
                List<DelayedJob> djs = findAllDelayedJobs(offset, limit);
                boolean stopLoop = false;
                while (!djs.isEmpty() && !stopLoop) {
                    for (DelayedJob dj : djs) {
                        DateTime timeToSubmit = DateTime.parse(dj.getDatetimeToSubmit());

                        if (now.isAfter(timeToSubmit)) {
                            // Submit this work item!
                            // TODO this should be done within a txn
                            deleteDelayedJob(dj.getId());
                            submit(dj.getQname(), dj.getItemId());
                        } else {
                            // Jobs come back in order of timeToSubmit
                            stopLoop = true;
                            break;
                        }

                        offset += limit;
                        djs = findAllDelayedJobs(offset, limit);
                    }
                }
                return null;
            }
        });
    }

    private long createItem(Jedis jedis, String qname, byte[] value) {
        long id = jedis.incr(seqName(qname));
        Item item = new Item(id, value);
        jedis.hset(itemsName(qname), String.valueOf(id), gson.toJson(item));
        return id;
    }

    private void updateItem(Jedis jedis, String qname, Item item) {
        jedis.hset(itemsName(qname), String.valueOf(item.getId()), gson.toJson(item));
    }

    private boolean deleteItem(Jedis jedis, String qname, long id) {
        return deleteItem(jedis, qname, String.valueOf(id));
    }

    private boolean deleteItem(Jedis jedis, String qname, String id) {
        return jedis.hdel(itemsName(qname), id) == 1;
    }

    private Item findItem(Jedis jedis, String qname, long id) {
        return findItem(jedis, qname, String.valueOf(id));
    }

    private Item findItem(Jedis jedis, String qname, String id) {
        String val = jedis.hget(itemsName(qname), id);

        if (val == null) {
            throw new ItemNotFoundException(qname, Long.parseLong(id));
        }

        byte[] bytes = u(val);
        return gson.fromJson(val, Item.class);
    }

    private void deleteDelayedJob(final long id) {
        client.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                jedis.hdel(u("delayed-jobs"), u(id));
                jedis.zrem(u("delayed-job-ids"), u(id));
                return null;
            }
        });
    }

    private byte[] u(long v) {
        return utf8Encode(String.valueOf(v));
    }

    private byte[] u(String v) {
        return utf8Encode(v);
    }

    private String qName(String qname) {
        return QUEUE_PREFIX + qname;
    }

    private String inProgressName(String qname) {
        return "__" + qName(qname) + "-in-progress";
    }

    private String seqName(String qname) {
        return "__" + qName(qname) + "-seq";
    }

    private String itemsName(String qname) {
        return "__" + qName(qname) + "-items";
    }

    private String pendingName(String qname) {
        return "__" + qName(qname) + "-pending";
    }

    private String failName(String qname) {
        return "__" + qName(qname) + "-fail";
    }

    public static class ItemNotFoundException extends RuntimeException {
        public ItemNotFoundException(String qname, long itemId) {
            super(String.format("Item not found [%s] [%d]", qname, itemId));
        }
    }

    public static class Item implements WorkQueue.Item {
        private long id;
        private Date creationDate;
        private Date lastInProgressDate;
        private long timeout;
        private int attemptCount;
        private String[] failureMessages;
        private byte[] value;

        Item(long id, byte[] value) {
            this.id = id;
            this.value = value;
            this.creationDate = new Date();
            this.lastInProgressDate = null;
            this.attemptCount = 0;
            this.failureMessages = new String[0];
        }

        public long getId() { return this.id; }
        public byte[] getValue() { return this.value; }
        public Date getCreationDate() { return this.creationDate; }

        public Date getLastInProgressDate() { return this.lastInProgressDate; }

        public void setLastInProgressDate(Date lastInProgressDate) {
            this.lastInProgressDate = lastInProgressDate;
        }

        public long getTimeout() { return this.timeout; }
        public void setTimeout(long timeout) { this.timeout = timeout; }

        public int getAttemptCount() { return this.attemptCount; }

        public void addFailureMessage(String message) {
            log.debug("Adding failure message [{}]", message);
            if (this.failureMessages == null) {
                this.failureMessages = new String[] {message};
            } else {
                this.failureMessages = Arrays.copyOf(
                        this.failureMessages, this.failureMessages.length + 1);
                this.failureMessages[this.failureMessages.length-1] = message;
            }
        }

        public String[] getFailureMessages() { return this.failureMessages; }

        public boolean isTimedOut() {
            return System.currentTimeMillis() > (this.lastInProgressDate.getTime() + this.timeout);
        }
    }
}
