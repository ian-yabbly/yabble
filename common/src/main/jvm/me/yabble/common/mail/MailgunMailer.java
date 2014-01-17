package me.yabble.common.mail;

import me.yabble.common.http.client.*;
import me.yabble.common.proto.CommonProtos.Email;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.mail.MailException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static me.yabble.common.SecurityUtils.*;
import static org.springframework.util.StringUtils.*;
import static java.util.Collections.EMPTY_LIST;

public class MailgunMailer {
    private static final Logger log = LoggerFactory.getLogger(MailgunMailer.class);

    private String baseUrl = "https://api.mailgun.net/v2";
    private String username = "api";
    private String password = "key-7iyzkhu04nk225kdk44pr-y7qab3uot3";
    private String mailDomain = "mail.yabbly.com";
    private HttpClient httpClient;

    public MailgunMailer(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void send(Email email) throws MailException {
        send(
                email.getFrom(),
                email.hasFromName() ? Optional.of(email.getFromName()) : Optional.<String>absent(),
                Optional.<String>absent(),
                email.getToList(),
                email.getCcList(),
                email.getBccList(),
                email.hasSubject() ? Optional.of(email.getSubject()) : Optional.<String>absent(),
                email.hasHtmlBody() ? Optional.of(email.getHtmlBody()) : Optional.<String>absent(),
                email.hasTextBody() ? Optional.of(email.getTextBody()) : Optional.<String>absent());
    }

    private void send(String from, Optional<String> fromName, Optional<String> replyTo, List<String> to, List<String> cc,
            List<String> bcc, Optional<String> subject, Optional<String> htmlBody, Optional<String> textBody)
        throws MailException
    {
        // Change from domain from yabbly.com to mail.yabbly.com.
        from = from.replaceAll("@yabbly.com", "@mail.yabbly.com");

        if (fromName.isPresent()) {
            from = String.format("%s <%s>", fromName.get(), from);
        }

        Map<String, String> params = Maps.newHashMap();
        params.put("from", from);

        final List<String> emails = Lists.newArrayList();

        boolean isTest = true;
        if (!to.isEmpty()) {
            params.put("to", collectionToDelimitedString(to, ","));
            if (isTest) {
                for (String e : to) {
                    emails.add(e);
                    if (!e.endsWith("-blackhole")) {
                        isTest = false;
                        break;
                    }
                }
            }
        }

        if (!cc.isEmpty()) {
            params.put("cc", collectionToDelimitedString(cc, ","));

            if (isTest) {
                for (String e : cc) {
                    emails.add(e);
                    if (!e.endsWith("-blackhole")) {
                        isTest = false;
                        break;
                    }
                }
            }
        }

        if (!bcc.isEmpty()) {
            log.info("bcc is not null and not empty");
            params.put("bcc", collectionToDelimitedString(bcc, ","));

            if (isTest) {
                for (String e : cc) {
                    emails.add(e);
                    if (!e.endsWith("-blackhole")) {
                        isTest = false;
                        break;
                    }
                }
            }
        }

        if (replyTo.isPresent()) {
            params.put("h:Reply-To", replyTo.get());
        }

        if (subject.isPresent()) {
            params.put("subject", subject.get());
        }

        if (htmlBody.isPresent()) {
            params.put("html", htmlBody.get());
        }

        if (textBody.isPresent()) {
            params.put("text", textBody.get());
        }

        if (isTest) {
            params.put("o:testmode", "true");
        }

        PostRequest post = new PostRequest(String.format("%s/%s/messages", baseUrl, mailDomain), params);

        post.addHeader("Authorization", "Basic " + base64Encode(utf8Encode(String.format("%s:%s", username, password))));
        post.addHeader("Content-Type", "application/x-www-form-urlencoded");
        post.setDoLog(true);

        httpClient.execute(post, new Function<Response, Void>() {
            @Override
            public Void apply(Response response) {
                try {
                    if (response.getStatusCode() != 200) {
                        // For some reason response.getContentType did not work here. It returns null.
                        if ("application/json".equalsIgnoreCase(response.getHeaderValue("Content-Type"))) {
                            JsonParser p = new JsonParser();
                            JsonObject j = p.parse(response.getContentAsString()).getAsJsonObject();

                            String message = null;
                            if (j.has("message")) { message = j.get("message").getAsString(); }

                            if (message != null) {
                                if (message.indexOf("not a valid address") >= 0) {
                                    throw new InvalidEmailAddressException(String.format(
                                            "To emails [%s]. Bad status code [%d]: %s",
                                            collectionToDelimitedString(emails, ", "),
                                            response.getStatusCode(),
                                            message));
                                } else {
                                    throw new RuntimeException(String.format(
                                            "Bad status code [%d]: %s",
                                            response.getStatusCode(),
                                            message));
                                }
                            }
                        }
                        throw new RuntimeException(String.format(
                                "Bad status code [%d]",
                                response.getStatusCode()));
                    } else {
                        String ret = response.getContentAsString();

                        JsonParser parser = new JsonParser();
                        JsonObject j = parser.parse(ret).getAsJsonObject();

                        if (!j.has("id")) {
                            throw new RuntimeException(String.format("\"id\" is missing from response body [%s]", ret));
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        });
    }
}
