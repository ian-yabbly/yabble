package me.yabble.common.http.client;

import org.apache.http.client.methods.HttpUriRequest;

import java.net.URI;

public interface RequestIF {
    public HttpUriRequest asHttpUriRequest();

    public void abort() throws UnsupportedOperationException;

    public boolean isAborted();

    public String getMethod();

    public URI getUri();

    public void addHeader(String name, String value);

    public boolean containsHeader(String name);

    public void removeHeaders(String name);

    public void setConnectionTimeout(int connectionTimeout);

    public void setSocketTimeout(int socketTimeout);

    public void setDoLog(boolean doLog);
}
