package me.yabble.common.http.client;

import com.google.common.base.Function;

import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.io.IOException;

/**
 * List of things TODO
 *
 * <ul>
 *   <li>Proxy configuration</li>
 *   <li>Add methods to get a limited number of bytes from the response</li>
 *   <li>Add execution context to RequestRetryHandler.shouldRetry</li>
 *   <li>HTTP protocol version</li>
 *   <li>HTTP protocol charset</li>
 *   <li>HTTP content charset</li>
 *   <li>User agent</li>
 *   <li>TCP no-delay (default and current behavior is no-delay)</li>
 *   <li>Socket buffer size</li>
 *   <li>Socket linger</li>
 *   <li>Stale connection check behavior</li>
 *   <li>Redirect behavior</li>
 *   <li>Compression</li>
 *   <li>Caching</li>
 *   <li>Non-blocking API</li>
 * </ul>
 */
public class HttpClientImpl implements HttpClient {
    private final static Logger log = LoggerFactory.getLogger(HttpClient.class);

    private static class ResponseHandlerAdapter<T>
        implements org.apache.http.client.ResponseHandler<T>
    {
        private ResponseHandler<T> handler;

        public ResponseHandlerAdapter(ResponseHandler<T> handler) {
            this.handler = handler;
        }

        public T handleResponse(HttpResponse response) throws IOException {
            try {
                return handler.handle(new Response(response));
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // Ensure that all resources are read (and therefore, closed)
                if (response.getEntity() != null) {
                    EntityUtils.consume(response.getEntity());
                }
            }
        }
    };

    private static class ByteArrayResponseHandler
        implements org.apache.http.client.ResponseHandler<byte[]>
    {
        public byte[] handleResponse(HttpResponse response) throws IOException {
            int sc = response.getStatusLine().getStatusCode();
            if (sc == 200) {
                return EntityUtils.toByteArray(response.getEntity());
            } else {
                throw new RuntimeException(String.format("Unexpected status code [%d]", sc));
            }
        }
    }

    /**
     * A specific character set can be set via the constructor. Otherwise, the character set from
     * the response is used, otherwise, ISO-8859-1 is used.
     */
    private static class StringResponseHandler
        implements org.apache.http.client.ResponseHandler<String>
    {
        private String charSet;

        public StringResponseHandler() {
            this.charSet = null;
        }

        public StringResponseHandler(String charSet) {
            this.charSet = charSet;
        }

        public String handleResponse(HttpResponse response) throws IOException {
            if (this.charSet == null) {
                return EntityUtils.toString(response.getEntity());
            } else {
                return EntityUtils.toString(response.getEntity(), this.charSet);
            }
        }
    }

    private static class FunctionResponseHandler<T> implements ResponseHandler<T> {
        private Function fn;

        public FunctionResponseHandler(Function<Response, T> fn) {
            this.fn = fn;
        }

        @Override
        public T handle(Response response) throws Exception {
            return (T) fn.apply(response);
        }
    }

    private DefaultHttpClient httpClient;
    private int _maxHttpConns;
    private int _maxHttpConnsPerRoute;
    private int _socketTimeoutMs;
    private long connPoolTimeoutMs;
    private String _userAgent;
    private ClientConnectionManager clientConnectionManager;

    public void setConnPoolTimeoutMs(long connPoolTimeoutMs) {
        this.connPoolTimeoutMs = connPoolTimeoutMs;
    }

    public void setMaxHttpConns(int maxHttpConns) {
        _maxHttpConns = maxHttpConns;
    }

    public void setMaxHttpConnsPerRoute(int maxHttpConnsPerRoute) {
        _maxHttpConnsPerRoute = maxHttpConnsPerRoute;
    }

    public void setSocketTimeoutMs(int socketTimeoutMs) {
        _socketTimeoutMs = socketTimeoutMs;
    }

    public void setUserAgent(String userAgent) {
        _userAgent = userAgent;
    }

    public void init() throws Exception {
        ConnPerRouteBean connPerRoute = new ConnPerRouteBean();
        connPerRoute.setDefaultMaxPerRoute(_maxHttpConnsPerRoute);

        HttpParams params = new BasicHttpParams();
        ConnManagerParams.setMaxTotalConnections(params, _maxHttpConns);
        ConnManagerParams.setMaxConnectionsPerRoute(params, connPerRoute);
        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);

        if (_userAgent != null) {
            HttpProtocolParams.setUserAgent(params, _userAgent);
        }

        HttpConnectionParams.setSoTimeout(params, _socketTimeoutMs);
        HttpConnectionParams.setConnectionTimeout(params, _socketTimeoutMs);
        params.setParameter("http.conn-manager.timeout", connPoolTimeoutMs);
        log.debug("Socket timeout milliseconds [{}]", _socketTimeoutMs);
        log.debug("Connection pool timeout milliseconds [{}]", connPoolTimeoutMs);
        
        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

        SSLSocketFactory sf = new SSLSocketFactory(SSLContext.getDefault());
        sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);

        Scheme https = new Scheme("https", sf, 443);
        schemeRegistry.register(https);

        clientConnectionManager = new ThreadSafeClientConnManager(params, schemeRegistry);
        httpClient = new DefaultHttpClient(clientConnectionManager, params);
        httpClient.addRequestInterceptor(new LoggingHttpRequestInterceptor());
        // Set this as the first response interceptor
        httpClient.addResponseInterceptor(new LoggingHttpResponseInterceptor(), 0);
    }

    public void shutdown() throws Exception {
        if (clientConnectionManager != null) {
            clientConnectionManager.shutdown();
        }
    }

    public byte[] executeToBytes(RequestIF request) {
        return executeToBytes(request, null);
    }

    public byte[] executeToBytes(RequestIF request, RequestRetryHandler retryHandler) {
        return execute(
                request.asHttpUriRequest(), new ByteArrayResponseHandler(), retryHandler);
    }

    public String executeToString(RequestIF request) {
        return executeToString(request, null);
    }

    public String executeToString(RequestIF request, RequestRetryHandler retryHandler) {
        return execute(request.asHttpUriRequest(), new StringResponseHandler(), retryHandler);
    }

    @Override
    public <T> T execute(RequestIF request, Function<Response, T> callback) {
        return (T) execute(request, new FunctionResponseHandler(callback));
    }

    public <T> T execute(RequestIF request, ResponseHandler<T> responseHandler) {
        return execute(request, responseHandler, null);
    }

    public <T> T execute(
            RequestIF request,
            ResponseHandler<T> responseHandler,
            RequestRetryHandler retryHandler)
    {
        return execute(
                request.asHttpUriRequest(),
                new ResponseHandlerAdapter<T>(responseHandler),
                retryHandler);
    }

    private <T> T execute(HttpUriRequest httpUriRequest, org.apache.http.client.ResponseHandler<T> responseHandler,
            RequestRetryHandler retryHandler)
    {
        int retryCount = 0;
        while (true) {
            try {
                log.info("Request: " + httpUriRequest.getURI());
                return httpClient.execute(httpUriRequest, responseHandler);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                if (retryHandler != null && retryHandler.shouldRetry(e, retryCount)) {
                    log.debug("Retrying request", e);
                    retryCount++;
                } else {
                    throw new RuntimeException(e);
                }
            } 
        }
    }
}
