package me.yabble.common.http.client;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIUtils;

import java.net.URISyntaxException;
import java.util.Map;

public class GetRequest extends AbstractRequest {
    private HttpGet httpGet;

    public GetRequest(String uri) {
        this.httpGet = new HttpGet(uri);
    }

    /**
     * @param uri the URI up to the query string
     * @param params a map representing the query parameters to be added to the end of the uri
     */
    public GetRequest(String uri, Map<String, String> params) {
        this(uri, params, "ISO-8859-1");
    }

    /**
     * @param uri the URI up to the query string
     * @param params a map representing the query parameters to be added to the end of the uri
     * @param paramEncoding The encoding to use while
     */
    public GetRequest(String uri, Map<String, String> params, String paramEncoding) {
        if (params != null && !params.isEmpty()) {
            this.httpGet = new HttpGet(uri + "?" + HttpUtils.mapToQuery(params, paramEncoding));
        } else {
            this.httpGet = new HttpGet(uri);
        }
    }

    public GetRequest(String scheme, String host, int port, String path, String query,
            String fragment)
        throws URISyntaxException
    {
        this.httpGet = new HttpGet(URIUtils.createURI(scheme, host, port, path, query, fragment));
    }

    public GetRequest(String scheme, String host, int port, String path,
            Map<String, String> params, String paramEncoding, String fragment)
        throws URISyntaxException
    {
        this.httpGet = new HttpGet(URIUtils.createURI(scheme, host, port, path,
                HttpUtils.mapToQuery(params, paramEncoding), fragment));
    }

    public HttpUriRequest asHttpUriRequest() {
        this.httpGet.setParams(getParams());
        return this.httpGet;
    }
}
