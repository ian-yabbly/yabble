package me.yabble.common.http.client;

import java.io.IOException;

public interface ResponseHandler<T> {
    public T handle(Response response) throws Exception;
}
