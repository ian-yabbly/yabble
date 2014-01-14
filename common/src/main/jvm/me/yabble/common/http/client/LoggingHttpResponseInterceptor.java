package me.yabble.common.http.client;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.HttpResponseInterceptor;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class LoggingHttpResponseInterceptor implements HttpResponseInterceptor {
    private static final Logger log = LoggerFactory.getLogger("http-resp");

    public void process(HttpResponse response, HttpContext context)
        throws HttpException, IOException
    {
        if (!response.getParams().getBooleanParameter(HttpClient.DO_LOG_PARAM, false)) {
            return;
        }

        log.info("Status line: " + response.getStatusLine());

        for (Header h : response.getAllHeaders()) {
            log.info("Header: " + h.getName() + ": " + h.getValue());
        }

        HttpEntity entity = response.getEntity();
        if (entity != null) {
            if (entity.isRepeatable()) {
                log.info(EntityUtils.toString(entity));
            } else {
                byte[] bytes = EntityUtils.toByteArray(entity);
                log.info(new String(bytes));
                response.setEntity(new ByteArrayEntity(bytes));
            }
        }
    }
}
