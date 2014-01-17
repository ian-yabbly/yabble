package me.yabble.web.service;

import me.yabble.common.ctx.ExecutionContext;
import me.yabble.common.redis.RedisClient;
import me.yabble.web.proto.WebProtos.Session;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;

import com.sun.net.httpserver.HttpExchange;

import org.apache.http.impl.cookie.DateUtils;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static me.yabble.common.SecurityUtils.base64Encode;

import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;

public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    // 60 days
    private static final int COOKIE_MAX_AGE_SECONDS = 60*24*60*60;

    private RedisClient redisClient;
    private String sessionCookieName;
    private String sessionCookieDomain;

    public SessionService(RedisClient redisClient, String sessionCookieName, String sessionCookieDomain) {
        this.redisClient = redisClient;
        this.sessionCookieName = sessionCookieName;
        this.sessionCookieDomain = sessionCookieDomain;
    }

    public Optional<Session> optional(String id) {
        try {
            return doFindSession(id);
        } catch (JedisConnectionException e) {
            return Optional.<Session>absent();
        }
    }
 
    public Session find(String id) {
        try {
            Optional<Session> optSession = optional(id);
            if (optSession.isPresent()) {
                return optSession.get();
            } else {
                throw new SessionNotFoundException(id);
            }
        } catch (JedisConnectionException e) {
            //log.error(e.getMessage(), e);
            throw new SessionNotFoundException(id);
        }
    }

    public Optional<Session> optional() {
        return find(false);
    }

    public Session find() {
        try {
            Optional<Session> s = find(false);
            if (!s.isPresent()) {
                throw new RuntimeException("Required session is not present");
            }
            return s.get();
        } catch (JedisConnectionException e) {
            //log.error(e.getMessage(), e);
            throw new RuntimeException("Required session is not present");
        }
    }

    public Session findOrCreate() {
        return find(true).get();
    }

    private Optional<Session> find(boolean create) {
        try {
            ExecutionContext ctx = ExecutionContext.getRequired();
            Optional<String> optSessionId = ctx.optionalAttribute("web-session-id", String.class);

            if (optSessionId.isPresent()) {
                Optional<Session> optSession = doFindSession(optSessionId.get());
                if (optSession.isPresent()) {
                    return optSession;
                } else if (create) {
                    // TODO Is this the right thing to do here?
                    return Optional.of(create());
                    /*
                    throw new RuntimeException(String.format(
                            "Expected session is not found in store [%s]",
                            optSessionId.get()));
                    */
                }
            } else if (create) {
                return Optional.of(create());
            }

            return Optional.<Session>absent();
        } catch (JedisConnectionException e) {
            if (create) {
                throw e;
            } else {
                //log.error(e.getMessage(), e);
                return Optional.<Session>absent();
            }
        }
    }

    public void maybeRemove() {
        try {
            ExecutionContext ctx = ExecutionContext.getRequired();
            final Optional<String> optSessionId = ctx.optionalAttribute("web-session-id", String.class);
            if (optSessionId.isPresent()) {
                remove();
            }
        } catch (JedisConnectionException e) {
            //log.error(e.getMessage(), e);
        }
    }

    public void remove() {
        ExecutionContext ctx = ExecutionContext.getRequired();
        final Optional<String> optSessionId = ctx.optionalAttribute("web-session-id", String.class);
        if (!optSessionId.isPresent()) {
            throw new RuntimeException("Cannot remove absent session");
        }

        ctx.removeAttribute("web-session-id");

        remove(optSessionId.get());
    }

    public void remove(final String id) {
        redisClient.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                Optional<Session> optSession = doFindSession(id, jedis);
                jedis.hdel("sessions", id);
                jedis.zrem("session-ids", id);
                if (optSession.isPresent()) {
                    Session session = optSession.get();
                    if (session.hasUserId()) {
                        jedis.srem(String.format("session-uid-%s-ids", session.getUserId()), id);
                    }
                }
                return null;
            }
        });
    }

    public List<Session> findAllLocked() {
        try {
            Set<String> ids = redisClient.work(new Function<Jedis, Set<String>>() {
                public Set<String> apply(Jedis jedis) {
                    return jedis.hkeys("session-locks");
                }
            });

            List<Session> ss = Lists.newArrayList();
            for (String id : ids) {
                Optional<Session> optSession = optional(id);
                if (optSession.isPresent()) {
                    ss.add(optSession.get());
                } else {
                    log.info("Session is not present [{}]", id);
                }
            }
            return ss;
        } catch (JedisConnectionException e) {
            //log.error(e.getMessage(), e);
            return Collections.EMPTY_LIST;
        }
    }

    public boolean isLocked(final String id) {
        try {
            return redisClient.work(new Function<Jedis, Boolean>() {
                public Boolean apply(Jedis jedis) {
                    return jedis.hexists("session-locks", id);
                }
            });
        } catch (JedisConnectionException e) {
            //log.error(e.getMessage(), e);
            return false;
        }
    }

    public Session withSession(boolean create, final Function<Session, Session> f) {
        return withSession(create, Optional.of(COOKIE_MAX_AGE_SECONDS), f);
    }

    public Session withSession(
            boolean create,
            Optional<Integer> optMaxCookieAge,
            final Function<Session, Session> f)
    {
        ExecutionContext ctx = ExecutionContext.getRequired();
        Optional<String> optSessionId = ctx.optionalAttribute("web-session-id", String.class);

        if (optSessionId.isPresent()) {
            // TODO The session may not exist in the backing store
            try {
                return withSession(optSessionId.get(), f);
            } catch (SessionNotFoundException e) {
                if (create) {
                    return withNewSession(optMaxCookieAge, f);
                } else {
                    throw e;
                }
            }
        } else {
            if (create) {
                return withNewSession(optMaxCookieAge, f);
            } else {
                throw new RuntimeException("Session ID is not present in execution context [session-id]");
            }
        }
    }

    public Session withNewSession(Optional<Integer> optMaxCookieAge, final Function<Session, Session> f) {
        return withSession(create(optMaxCookieAge).getId(), f);
    }

    private Session withSession(final String id, final Function<Session, Session> f) {
        return withOptionalSession(id, new Function<Optional<Session>, Optional<Session>>() {
            @Override
            public Optional<Session> apply(Optional<Session> optSession) {
                if (optSession.isPresent()) {
                    return Optional.of(f.apply(optSession.get()));
                } else {
                    throw new SessionNotFoundException(id);
                }
            }
        }).get();
    }

    public Optional<Session> withOptionalSession(final String id, final Function<Optional<Session>, Optional<Session>> f) {
        ExecutionContext ctx = ExecutionContext.getOrCreate();

        if (ctx.hasAttribute("locked-session-id")) {
            String lockedSessionId = (String) ctx.getRequiredAttribute("locked-session-id");
            if (lockedSessionId.equals(id)) {
                throw new RuntimeException(String.format("Session is already locked [{}]", id));
            } else {
                throw new RuntimeException(String.format(
                        "Mismatch session IDs [found in context:%s] [given:%s]",
                        lockedSessionId, id));
            }
        }

        boolean isSessionLocked = false;
        try {
            redisClient.work(new Function<Jedis, Void>() {
                public Void apply(Jedis jedis) {
                    long sleepTime = 20l;

                    while (1l != jedis.hsetnx("session-locks", id, DateTime.now().toString())) {
                        try {
                            log.info("Sleeping waiting for session to unlock [{}]", sleepTime);
                            Thread.sleep(sleepTime);
                        } catch (InterruptedException e) { throw new RuntimeException(e); }
                        sleepTime *= 2;

                        if (sleepTime > 3000) {
                            log.error("LOCK SESSION: Forcing un-lock of session [{}]", id);
                            jedis.hdel("session-locks", id);
                            if (1l != jedis.hsetnx("session-locks", id, DateTime.now().toString())) {
                                throw new RuntimeException("Could not get a lock on the session");
                            }
                            break;
                        }
                    }
                    return null;
                }
            });

            isSessionLocked = true;

            ctx.removeAttribute("session");
            ctx.setAttribute("locked-session-id", id);

            Optional<Session> optSession = f.apply(optional(id));
            if (optSession.isPresent()) {
                persist(optSession.get());
            }

            return optSession;
        } finally {
            if (isSessionLocked) { unlockSession(id); }
        }
    }

    private void unlockSession(final String id) {
        redisClient.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                jedis.hdel("session-locks", id);
                return null;
            }
        });

        ExecutionContext ctx = ExecutionContext.getRequired();
        ctx.removeAttribute("locked-session-id");
    }

    private Optional<Session> doFindSession(final String id) {
        return redisClient.work(new Function<Jedis, Optional<Session>>() {
            public Optional<Session> apply(Jedis jedis) {
                return doFindSession(id, jedis);
            }
        });
    }

    private Optional<Session> doFindSession(String id, Jedis jedis) {
        try {
            String v = jedis.hget("sessions", id);
            if (v == null) { return Optional.absent(); }
            return Optional.of(Session.parseFrom(base64Encode(v)));
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
            log.warn("InvalidProtocolBufferException caught for session [{}]. Removing session.", id);
            jedis.hdel("sessions", id);
            jedis.zrem("session-ids", id);
            // TODO `session-uid-%d-ids` may still be populated
            return Optional.<Session>absent();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void persist(final Session session) {
        redisClient.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                try {
                    String v = base64Encode(session.toByteArray());
                    jedis.hset("sessions", session.getId(), v);
                    jedis.zadd("session-ids", session.getId().hashCode(), session.getId());
                    if (session.hasUserId()) {
                        jedis.sadd(String.format("session-uid-%s-ids", session.getUserId()), session.getId());
                    }
                    return null;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private Session create() {
        return create(Optional.of(COOKIE_MAX_AGE_SECONDS));
    }

    private Session create(Optional<Integer> optMaxCookieAge) {
        ExecutionContext ctx = ExecutionContext.getRequired();
        String sessionId = randomAlphanumeric(32).toLowerCase();

        ctx.setAttribute("web-session-id", sessionId);

        DateTime now = DateTime.now();
        Session session = Session.newBuilder()
                .setId(sessionId)
                .setCreationDate(now.toString())
                .build();
        persist(session);

        HttpExchange exchange = (HttpExchange) ctx.getAttribute("http-exchange");
        if (null != exchange) {
            DateTime cookieExpr = new DateTime(DateTimeZone.UTC);
            cookieExpr = cookieExpr.plusSeconds(COOKIE_MAX_AGE_SECONDS);
            exchange.getResponseHeaders().add("Set-Cookie", String.format("%s=%s; Path=/; Domain=%s; Expires=%s;",
                    sessionCookieName,
                    sessionId,
                    sessionCookieDomain,
                    DateUtils.formatDate(cookieExpr.toDate())));
        } else {
            log.info("Not setting HTTP session cookie because http-exchange is not present in execution context");
        }

        return session;
    }
}

