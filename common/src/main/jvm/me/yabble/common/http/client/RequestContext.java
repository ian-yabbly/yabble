package me.yabble.common.http.client;

import java.util.HashMap;
import java.util.Map;

public class RequestContext {
    private Map<String, Object> attrs;

    public RequestContext() {
        this.attrs = new HashMap<String, Object>();
    }

    public Object getAttribute(String key) {
        return this.attrs.get(key);
    }

    public Object setAttribute(String key, Object value) {
        // Do not allow null values to avoid confusion
        if (value == null) {
            throw new NullPointerException("null values not allowed");
        }
        return this.attrs.put(key, value);
    }

    public Object removeAttribute(String key) {
        return this.attrs.remove(key);
    }
}
