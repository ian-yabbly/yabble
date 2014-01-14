package me.yabble.common.http.client;

public interface RequestRetryHandler {
    public boolean shouldRetry(Throwable t, int retryCount);
}
