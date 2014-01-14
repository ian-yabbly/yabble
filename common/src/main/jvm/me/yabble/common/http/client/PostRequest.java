package me.yabble.common.http.client;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

public class PostRequest extends AbstractRequest {
    private HttpPost httpPost;

    public PostRequest(String uri, String payload, String charset) throws HttpClientException {
        try {
            this.httpPost = new HttpPost(uri);
            this.httpPost.setEntity(new StringEntity(payload, charset));
        } catch (UnsupportedEncodingException e) {
            throw new HttpClientException(e);
        }
    }

    public PostRequest(String uri, String payload) throws HttpClientException {
        try {
            this.httpPost = new HttpPost(uri);
            this.httpPost.setEntity(new StringEntity(payload));
        } catch (UnsupportedEncodingException e) {
            throw new HttpClientException(e);
        }
    }

    public PostRequest(String uri, byte[] payload) {
        this.httpPost = new HttpPost(uri);
        this.httpPost.setEntity(new ByteArrayEntity(payload));
    }

    public PostRequest(String uri, InputStream payload, long length) {
        this.httpPost = new HttpPost(uri);
        this.httpPost.setEntity(new InputStreamEntity(payload, length));
    }

    /**
     * @param uri the URI up to the query string
     * @param params a map representing the query parameters to be added to the end of the uri
     */
    public PostRequest(String uri, Map<String, String> params) {
        this(uri, params, "ISO-8859-1");
    }

    /**
     * @param uri the URI up to the query string
     * @param params a map representing the query parameters to be added to the end of the uri
     * @param paramEncoding The encoding to use while
     */
    public PostRequest(String uri, Map<String, String> params, String paramEncoding)
        throws HttpClientException
    {
        try {
            this.httpPost = new HttpPost(uri);
            this.httpPost.setEntity(new StringEntity(HttpUtils.mapToQuery(params, paramEncoding)));
        } catch (UnsupportedEncodingException e) {
            throw new HttpClientException(e);
        }
    }

    public HttpUriRequest asHttpUriRequest() {
        this.httpPost.setParams(getParams());
        return this.httpPost;
    }
}
