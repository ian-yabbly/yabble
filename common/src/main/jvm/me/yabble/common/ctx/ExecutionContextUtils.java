package me.yabble.common.ctx;

import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutionContextUtils {
    private final static Logger log = LoggerFactory.getLogger(ExecutionContextUtils.class);

    private static final ThreadLocal<ExecutionContext> executionContexts = new ThreadLocal<ExecutionContext>();

    public static void set(ExecutionContext context) {
        executionContexts.set(context);
    }

    public static Optional<ExecutionContext> get(boolean create) {
        ExecutionContext context = (ExecutionContext) executionContexts.get();
        if (context == null && create) {
            context = new ExecutionContext();
            set(context);
        }
        return Optional.fromNullable(context);
    }

    public static ExecutionContext getNew() {
        Optional<ExecutionContext> opt = get(false);
        if (opt.isPresent()) {
            throw new RuntimeException("Unexpected execution context is present");
        }
        ExecutionContext context = new ExecutionContext();
        set(context);
        return context;
    }

    public static Optional<ExecutionContext> getOptional() {
        return get(false);
    }

    public static ExecutionContext getRequired() {
        Optional<ExecutionContext> opt = get(false);
        if (!opt.isPresent()) {
            throw new RuntimeException("Required execution context is not set");
        }
        return opt.get();
    }

    public static ExecutionContext getOrCreate() {
        Optional<ExecutionContext> opt = get(true);
        return opt.get();
    }

    public static Optional<ExecutionContext> get() {
        return get(false);
    }

    public static Optional<Object> removeAttribute(String name) {
        Optional<ExecutionContext> optCtx = getOptional();
        if (optCtx.isPresent()) {
            return optCtx.get().removeAttribute(name);
        }
        return Optional.<Object>absent();
    }

    public static boolean hasAttribute(String name) {
        Optional<ExecutionContext> optCtx = getOptional();
        return optCtx.isPresent() && optCtx.get().hasAttribute(name);
    }

    public static void remove() {
        executionContexts.remove();
    }
}
