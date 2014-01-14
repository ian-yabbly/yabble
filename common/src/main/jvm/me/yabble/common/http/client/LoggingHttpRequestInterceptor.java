package me.yabble.common.http.client;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoggingHttpRequestInterceptor implements HttpRequestInterceptor {
    private static final Logger log = LoggerFactory.getLogger("http-req");

    public void process(HttpRequest request, HttpContext context)
        throws HttpException, IOException
    {
        if (!request.getParams().getBooleanParameter(HttpClient.DO_LOG_PARAM, false)) {
            return;
        }

        log.info("Request line: " + request.getRequestLine());

        for (Header h : request.getAllHeaders()) {
            log.info("Header: " + h.getName() + ": " + h.getValue());
        }

        if (request instanceof HttpEntityEnclosingRequest) {
            HttpEntityEnclosingRequest heer = (HttpEntityEnclosingRequest) request;
            HttpEntity entity = heer.getEntity();
            if (entity.isRepeatable()) {
                log.info(EntityUtils.toString(entity));
            } else {
                log.info("Not logging POST data because the HTTP entity is not repeatable");
            }
        }
    }
}
