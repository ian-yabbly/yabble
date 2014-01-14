package me.yabble.common.http.client;

import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.StringEntity;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.Map;

public class PutRequest extends AbstractRequest {
    private HttpPut httpPut;

    public PutRequest(String uri, String payload, String charset) {
        try {
            httpPut = new HttpPut(uri);
            httpPut.setEntity(new StringEntity(payload, charset));
        } catch (UnsupportedEncodingException e) {
            throw new HttpClientException(e);
        }
    }

    public PutRequest(String scheme, String host, int port, String path, String payload, String charset)
        throws URISyntaxException
    {
        this(scheme, host, port, path, null, null, payload, charset);
    }

    public PutRequest(String scheme, String host, int port, String path, String query,
            String fragment, String payload, String charset)
        throws URISyntaxException
    {
        try {
            httpPut = new HttpPut(URIUtils.createURI(scheme, host, port, path, query, fragment));
            httpPut.setEntity(new StringEntity(payload, charset));
        } catch (UnsupportedEncodingException e) {
            throw new HttpClientException(e);
        }
    }

    public HttpUriRequest asHttpUriRequest() {
        httpPut.setParams(getParams());
        return httpPut;
    }
}
