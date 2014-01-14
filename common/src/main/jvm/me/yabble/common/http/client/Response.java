package me.yabble.common.http.client;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Response {
    private HttpResponse httpResponse;

    Response(HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }

    public int getStatusCode() {
        return httpResponse.getStatusLine().getStatusCode();
    }

    public byte[] getContentAsBytes() throws IOException {
        return EntityUtils.toByteArray(httpResponse.getEntity());
    }

    public String getContentAsString() throws IOException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) { return null; }
        return EntityUtils.toString(entity);
    }

    public String getContentAsString(String charset) throws IOException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) { return null; }
        return EntityUtils.toString(entity, charset);
    }

    public InputStream getContent() throws IOException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) { return null; }
        return entity.getContent();
    }

    public void writeContentTo(OutputStream os) throws IOException {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) { return; }
        entity.writeTo(os);
    }

    public long getContentLength() {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) { return 0L; }
        return entity.getContentLength();
    }

    public String getContentType() {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) { return null; }
        Header h = entity.getContentType();
        if (h == null) { return null; }
        return h.getValue();
    }

    public String getContentEncoding() {
        HttpEntity entity = httpResponse.getEntity();
        if (entity == null) { return null; }
        Header h = entity.getContentEncoding();
        if (h == null) { return null; }
        return h.getValue();
    }

    /**
     * Returns the first header value with the given name, otherwise null.
     */
    public String getFirstHeaderValue(String name) {
        Header h = httpResponse.getFirstHeader(name);
        if (h == null) { return null; }
        return h.getValue();
    }

    /**
     * Returns the last header value with the given name, otherwise null.
     */
    public String getLastHeaderValue(String name) {
        Header h = httpResponse.getLastHeader(name);
        if (h == null) { return null; }
        return h.getValue();
    }

    public List<String> getHeaderValues(String name) {
        List<String> values = new ArrayList<String>();
        for (Header h : httpResponse.getHeaders(name)) {
            values.add(h.getValue());
        }
        return values;
    }

    public String getHeaderValue(String name) {
        return this.getHeaderValue(name, true);
    }

    public String getHeaderValue(String name, boolean strict) {
        List<String> values = this.getHeaderValues(name);

        if (values.isEmpty()) { return null; }

        if (values.size() > 1 && strict) {
            // TODO come up with the right exception to throw here
            throw new RuntimeException(
                    String.format("More than one header with name %s exists", name));
        }

        return values.get(0);
    }

    public Map<String, String> getAllHeaders() {
        Map<String, String> headers = new HashMap<String, String>();
        for (Header h : httpResponse.getAllHeaders()) {
            headers.put(h.getName(), h.getValue());
        }
        return headers;
    }
}
