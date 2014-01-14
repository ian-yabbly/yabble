package me.yabble.common.http.client;

import java.io.IOException;

/**
 * Allows clients to easily process HTTP responses without returning content.
 */
public abstract class AbstractVoidResponseHandler implements ResponseHandler<Void> {
    public Void handle(Response response) throws Exception {
        handleWithoutResult(response);
        return null;
    }

    public abstract void handleWithoutResult(Response response) throws Exception;
}
