package me.yabble.common.ctx;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExecutionContext {
    private Map<String, Object> attrs = new HashMap<String, Object>();

    public static ExecutionContext getOrCreate() {
        return ExecutionContextUtils.getOrCreate();
    }

    public static scala.Option<ExecutionContext> option() {
      return scala.Option.apply(getOptional().orNull());
    }

    public static Optional<ExecutionContext> optional() {
        return getOptional();
    }

    public static Optional<ExecutionContext> getOptional() {
        return ExecutionContextUtils.getOptional();
    }

    public static ExecutionContext getRequired() {
        return ExecutionContextUtils.getRequired();
    }

    public static void remove() {
        ExecutionContextUtils.remove();
    }

    public Object getAttribute(String key) {
        return attrs.get(key);
    }

    public <T> Optional<T> optionalAttribute(String key, Class<T> type) {
        return Optional.fromNullable((T) getAttribute(key));
    }

    public <T> T getAttribute(String key, T fallback) {
        T t = (T) attrs.get(key);
        if (t == null) {
            return fallback;
        }
        return t;
    }

    public <T> T getRequiredAttribute(String key, Class<T> type) {
        return (T) getRequiredAttribute(key);
    }

    public Object getRequiredAttribute(String key) {
        Object o = attrs.get(key);
        if (o == null) {
            throw new RuntimeException(String.format("Required attribute [%s] is null", key));
        }
        return o;
    }

    public boolean booleanAttribute(String name, boolean fallback) {
        Optional<Boolean> optAttr = optionalAttribute(name, Boolean.class);
        if (optAttr.isPresent()) {
            return optAttr.get();
        } else {
            return fallback;
        }
    }

    public Optional<Object> removeAttribute(String key) {
        return Optional.fromNullable(attrs.remove(key));
    }

    public void setAttribute(String key, Object value) {
        attrs.put(key, value);
    }

    public boolean hasAttribute(String key) {
        return attrs.containsKey(key);
    }

    /*
     * Appends to an existing List or creates a new single element
     * list.
     */
    public boolean addToList(String key, Object value) {
        boolean created = false;

        List l = (List) getAttribute(key);
        if (l == null) {
            created = true;
            l = Lists.newArrayList();
            setAttribute(key, l);
        }

        l.add(value);

        return created;
    }

    /*
     * Appends to an existing List or creates a new single element
     * list.
     */
    public boolean addToSet(String key, Object value) {
        boolean created = false;

        Set s = (Set) getAttribute(key);
        if (s == null) {
            created = true;
            s = Sets.newLinkedHashSet();
            setAttribute(key, s);
        }

        s.add(value);

        return created;
    }
}
