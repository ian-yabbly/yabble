package me.yabble.common.wq;

import me.yabble.common.proto.CommonProtos.DelayedJob;

import com.google.common.base.Optional;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface WorkQueue {

    static interface Item {
        public long getId();
        public byte[] getValue();
        public boolean isTimedOut();
        public int getAttemptCount();
    }

    /**
     * Add an item to the queue in a pending state after the specified delay has elapsed.
     */
    void submit(String qname, byte[] value, long delay, TimeUnit delayUnit);

    /**
     * Add an item to the queue in a pending state.
     */
    void submit(String qname, byte[] value);

    /**
     * To be used only by DelayedJobWorker
     */
    long submit(String qname, long itemId);

    /**
     * Take an item off the queue for processing. If commit or rollback is not called
     * in the given timeout period, the item may be removed from the in-progress queue.
     */
    Optional<Item> begin(String qname, long timeout, TimeUnit unit);

    /**
     * Same as begin(String, long, TimeUnit), but will block for the given amount of time
     * if no items are immediately available.
     */
    Optional<Item> begin(
            String qname, long timeout, TimeUnit unit, long waitTimeout, TimeUnit waitUnit);

    /**
     * Mark an item taken off the queue (begin) as completed.
     */
    void commit(String qname, long id);

    /**
     * Put an item back on the work queue (after a begin) for future processing.
     */
    void rollback(String qname, long id, String message);

    /**
     * Take an item from an in-progress state an move it to an failed state.
     */
    void fail(String qname, long id, String message);

    /**
     * Take an item from a failed state an move it to a pending state.
     */
    void retry(String qname, long id);

    /**
     * Delete all items on a work queue.
     */
    void delete(String qname);

    /**
     * Delete a single item from a work queue.
     */
    boolean delete(String qname, long id);

    long size(String qname);

    long inProgressSize(String qname);

    long failSize(String qname);

    List<Item> getItems(String qname, int offset, int limit);

    List<Item> getInProgressItems(String qname, int offset, int limit);

    List<Item> getFailedItems(String qname, int offset, int limit);

    List<String> getAllQueueNames();

    List<DelayedJob> findAllDelayedJobs(final int offset, final int limit);

    void undelayAllJobs();

    void undelayJob(long id);
}
