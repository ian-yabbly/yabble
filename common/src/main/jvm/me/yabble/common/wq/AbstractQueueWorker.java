package me.yabble.common.wq;

import me.yabble.common.ctx.ExecutionContext;
import me.yabble.common.ctx.ExecutionContextUtils;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.Lifecycle;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import static java.util.concurrent.TimeUnit.*;

public abstract class AbstractQueueWorker implements Lifecycle, Runnable {
    private static final Logger log = LoggerFactory.getLogger(AbstractQueueWorker.class);
    private static final long MAX_BACKOFF_SLEEP_TIME = 300l;

    private TransactionTemplate txnTemplate;
    private WorkQueue workQueue;
    private String qname;
    private String threadName;
    private int maxRetries;

    private ExecutorService executorService;
    private boolean isRunning = false;

    public AbstractQueueWorker(TransactionTemplate txnTemplate, WorkQueue workQueue, String qname, String threadName, int maxRetries) {
        this.txnTemplate = txnTemplate;
        this.workQueue = workQueue;
        this.qname = qname;
        this.threadName = threadName;
        this.maxRetries = maxRetries;
    }

    @Override
    public void start() {
        this.executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, threadName);
                t.setDaemon(true);
                return t;
            }
        });
        this.executorService.submit(this);
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
        long backoffSleepTime = 1l;

        while (true) {
            log.debug("Starting loop");

            try {
                Optional<WorkQueue.Item> oitem = workQueue.begin(
                        qname, 10, MINUTES, 5, MINUTES);

                if (oitem.isPresent()) {
                    final WorkQueue.Item item = oitem.get();
                    log.debug("Work item retrieved [{}]", item.getValue());

                    txnTemplate.execute(new TransactionCallback<Void>() {
                        public Void doInTransaction(TransactionStatus status) {
                            boolean isSuccess = false;
                            String errorMessage = null;
                            try {
                                handleWorkItem(item, status);
                                isSuccess = true;
                            } catch (FailException e) {
                                Throwable cause = e;
                                if (e.getCause() != null) {
                                    cause = e.getCause();
                                }
                                status.setRollbackOnly();
                                errorMessage = String.format("%s: %s",
                                        cause.getClass().getSimpleName(), cause.getMessage());
                            } catch (Throwable e) {
                                status.setRollbackOnly();
                                errorMessage = String.format("%s: %s",
                                        e.getClass().getSimpleName(), e.getMessage());
                                log.error(e.getMessage(), e);
                            } finally {
                                try {
                                    if (item != null) {
                                        if (isSuccess) {
                                            workQueue.commit(qname, item.getId());
                                        } else {
                                            if (item.getAttemptCount() >= maxRetries) {
                                                log.warn("Work queue item has failed [{}]", item.getId());
                                                handleFailure(item, errorMessage);
                                                workQueue.fail(qname, item.getId(), errorMessage);
                                            } else {
                                                log.info("Rolling back item [{}]", item.getId());
                                                workQueue.rollback(qname, item.getId(), errorMessage);
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("Unexpected exception [{}]", e.getMessage(), e);
                                }
                            }

                            return null;
                        }
                    });
                } else {
                    log.trace("No work item retrieved");
                }

                backoffSleepTime = 1l;
            } catch (Exception e) {
                try {
                    log.warn(e.getMessage(), e);
                    long t = Math.min(backoffSleepTime, MAX_BACKOFF_SLEEP_TIME);
                    log.info("Sleeping for [{}] seconds", t);
                    Thread.sleep(SECONDS.toMillis(t));
                } catch (InterruptedException e2) {
                    log.warn(e2.getMessage(), e2);
                }
                backoffSleepTime *= 2;
            }
        }
    }

    protected WorkQueue getWorkQueue() { return workQueue; }

    protected abstract void handleWorkItem(WorkQueue.Item item, TransactionStatus status)
        throws Exception;

    protected abstract void handleFailure(WorkQueue.Item item, String errorMessage)
        throws Exception;
}
