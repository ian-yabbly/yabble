package me.yabble.common.http.client;

import com.google.common.base.Function;

public interface HttpClient {
    public static final String DO_LOG_PARAM = "me.yabble.common.http.client.do-log";

    public byte[] executeToBytes(RequestIF request);

    public byte[] executeToBytes(RequestIF request, RequestRetryHandler retryHandler);

    public String executeToString(RequestIF request);

    public String executeToString(RequestIF request, RequestRetryHandler retryHandler);

    public <T> T execute(RequestIF request, Function<Response, T> callback);

    public <T> T execute(RequestIF request, ResponseHandler<T> responseHandler);

    public <T> T execute(
            RequestIF request,
            ResponseHandler<T> responseHandler,
            RequestRetryHandler retryHandler);
}
