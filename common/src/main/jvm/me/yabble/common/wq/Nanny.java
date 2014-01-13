package me.yabble.common.wq;

import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.Lifecycle;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import static java.util.concurrent.TimeUnit.*;

public class Nanny implements Runnable, Lifecycle {
    private static final Logger log = LoggerFactory.getLogger("work-queue-nanny");
    private static final int PAGE_SIZE = 1024;

    private WorkQueue workQueue;
    private int maxRetries;

    private Gson gson = new Gson();
    private ScheduledExecutorService executorService;
    private boolean isRunning = false;

    public Nanny(WorkQueue workQueue, int maxRetries) {
        this.workQueue = workQueue;
        this.maxRetries = maxRetries;
    }

    @Override
    public void start() {
        this.executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                return new Thread(r, "work-queue-nanny");
            }
        });
        this.executorService.scheduleWithFixedDelay(this, 10l, 60l, SECONDS);
        this.isRunning = true;
    }

    @Override
    public void stop() {
        this.executorService.shutdown();
        this.isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return this.isRunning;
    }

    @Override
    public void run() {
        log.debug("Running");
        for (String qname : workQueue.getAllQueueNames()) {
            log.debug("Running [{}]", qname);
            long size = workQueue.inProgressSize(qname);
            log.debug("In-progress size [{}]", size);

            for (int offset = 0; offset < size; offset += PAGE_SIZE) {
                for (WorkQueue.Item i : workQueue.getInProgressItems(qname, offset, PAGE_SIZE)) {
                    log.debug("Looking at work item [{}]", i.getId());
                    try {
                        if (i.isTimedOut()) {
                            if (i.getAttemptCount() >= maxRetries) {
                                log.debug("Failing work item [{}]", i.getId());
                                workQueue.fail(qname, i.getId(), "Work item has timed out");
                            } else {
                                log.debug("Rolling back work item [{}]", i.getId());
                                workQueue.rollback(
                                        qname, i.getId(), "Work item has timed out");
                            }
                        }
                    } catch (Throwable t) {
                        log.error(
                                String.format("Caught exception rolling back work item [%s] [%s]",
                                    t.getMessage(), this.gson.toJson(i)),
                                t);
                    }
                }
            }
        }
    }
}
