package me.yabble.common.http.client;

public class CountingSleepingRequestRetryHandler implements RequestRetryHandler {
    private int maxRetries;
    private long sleepMs;

    public CountingSleepingRequestRetryHandler(int maxRetries, long sleepMs) {
        this.maxRetries = maxRetries;
        this.sleepMs = sleepMs;
    }

    public boolean shouldRetry(Throwable t, int retryCount) {
        if (retryCount < maxRetries) {
            try {
                Thread.sleep(this.sleepMs);
            } catch (InterruptedException ignored) { ; }
            return true;
        } else {
            return false;
        }
    }
}
