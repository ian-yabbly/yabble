package me.yabble.common.http.client;

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;

import java.net.URI;

abstract class AbstractRequest implements RequestIF {

    private HttpParams _httpParams;

    public AbstractRequest() {
        _httpParams = new BasicHttpParams();
    }

    public abstract HttpUriRequest asHttpUriRequest();

    public void abort() throws UnsupportedOperationException {
        this.asHttpUriRequest().abort();
    }

    public boolean isAborted() {
        return this.asHttpUriRequest().isAborted();
    }

    public String getMethod() {
        return this.asHttpUriRequest().getMethod();
    }

    public URI getUri() {
        return this.asHttpUriRequest().getURI();
    }

    public void addHeader(String name, String value) {
        this.asHttpUriRequest().addHeader(name, value);
    }

    public boolean containsHeader(String name) {
        return this.asHttpUriRequest().containsHeader(name);
    }

    public void removeHeaders(String name) {
        this.asHttpUriRequest().removeHeaders(name);
    }

    public void setConnectionTimeout(int connectionTimeout) {
        _httpParams.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, connectionTimeout);
    }

    public void setSocketTimeout(int socketTimeout) {
        _httpParams.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, socketTimeout);
    }

    protected HttpParams getParams() {
        return _httpParams;
    }

    public void setDoLog(boolean doLog) {
        _httpParams.setBooleanParameter(HttpClient.DO_LOG_PARAM, doLog);
    }
}
