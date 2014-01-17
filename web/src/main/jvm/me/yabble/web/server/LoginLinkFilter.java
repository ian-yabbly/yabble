package me.yabble.web.server;

import me.yabble.common.SecurityUtilsBean;
import me.yabble.service.model.User;
import me.yabble.web.proto.WebProtos.Session;
import me.yabble.web.service.SessionService;
import me.yabble.web.handler.Utils;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

import com.sun.net.httpserver.*;

import org.joda.time.LocalDate;
        
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
    
import java.io.IOException;

public class LoginLinkFilter extends Filter {
    private static final Logger log = LoggerFactory.getLogger(LoginLinkFilter.class);

    private SessionService sessionService;
    private SecurityUtilsBean securityUtilsBean;

    public LoginLinkFilter(SecurityUtilsBean securityUtilsBean, SessionService sessionService) {
        this.securityUtilsBean = securityUtilsBean;
        this.sessionService = sessionService;
    }

    @Override
    public String description() { return "login-link-filter"; }

    @Override
    public void doFilter(HttpExchange exchange, Filter.Chain chain) throws IOException {
        final String path = Utils.noContextPath(exchange);

        if (path.startsWith("/l/")) {
            // Format is /l/the/normal/url/{uid}/{local-date}/{signature}

            Optional<Session> optSession = sessionService.optional();

            String[] parts = Iterables.toArray(Splitter.on('/').split(path), String.class);

            String signature = parts[parts.length-1];
            LocalDate localDate = LocalDate.parse(parts[parts.length-2]);
            final String uid = parts[parts.length-3];

            // First check that the signature is correct
            String[] sigParts = new String[parts.length-1];
            System.arraycopy(parts, 0, sigParts, 0, sigParts.length);
            String testSig = securityUtilsBean.sign(uid, Joiner.on('/').join(sigParts));
            if (!signature.equals(testSig)) {
                // TODO Put up a flash message here
                Utils.redirectResponse(exchange, "/", true);
            } else {
                if (optSession.isPresent()) {
                    Session session = optSession.get();
                    if (session.hasUserId()) {
                      if (!session.getUserId().equals(uid)) {
                          log.info("Switching session user because of external login link from [{}] to [{}]", session.getUserId(), uid);
                          sessionService.withSession(true, new Function<Session, Session>() {
                              @Override
                              public Session apply(Session session) {
                                  return session.toBuilder().setUserId(uid).build();
                              }
                          });
                      }
                    } else {
                        log.info("Setting user [{}]", uid);
                        sessionService.withSession(true, new Function<Session, Session>() {
                            @Override
                            public Session apply(Session session) {
                                return session.toBuilder().setUserId(uid).build();
                            }
                        });
                    }
                } else {
                    log.info("Setting user [{}]", uid);
                    sessionService.withSession(true, new Function<Session, Session>() {
                        @Override
                        public Session apply(Session session) {
                            return session.toBuilder().setUserId(uid).build();
                        }
                    });
                }

                String redirectPath = null;
                if (parts.length == 5) {
                    redirectPath = "/";
                } else {
                    String[] redirectParts = new String[parts.length-4];
                    System.arraycopy(parts, 2, redirectParts, 1, parts.length-5);
                    redirectParts[0] = "";
                    redirectPath = Joiner.on('/').join(redirectParts);
                }

                String query = exchange.getRequestURI().getQuery();
                if (query != null && !"".equals(query)) {
                    redirectPath += "?" + query;
                }

                Utils.redirectResponse(exchange, redirectPath, false);
            }
        } else {
            chain.doFilter(exchange);
        }
    }
}
