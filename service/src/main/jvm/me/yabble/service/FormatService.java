package me.yabble.service;

import me.yabble.common.SecurityUtils;
import me.yabble.common.TextFormat;
import me.yabble.common.redis.RedisClient;

import com.google.common.base.Function;
import com.google.common.base.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.Jedis;

import scala.Option;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import static java.util.regex.Pattern.*;

public class FormatService {
    private static final Logger log = LoggerFactory.getLogger(FormatService.class);

    private static final Pattern LINK_PATTERN = Pattern.compile("(\\(?\\bhttps?://([-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]))", CASE_INSENSITIVE);
    private static final Pattern AT_USER_NAME_PATTERN = Pattern.compile("@([\\p{Alnum}-_]+)", UNICODE_CHARACTER_CLASS);

    private RedisClient redisClient;

    public FormatService(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    public String formatUserMarkup(Optional<String> optIn, boolean includeAnchors) {
        if (optIn == null || !optIn.isPresent()) { return ""; }
        return formatUserMarkup(optIn.get(), includeAnchors);
    }

    public String formatUserMarkup(Option<String> optIn, boolean includeAnchors) {
        if (optIn == null || optIn.isEmpty()) { return ""; }
        return formatUserMarkup(optIn.get(), includeAnchors);
    }

    public String formatUserMarkup(String in, final boolean includeAnchors) {
        if (in == null) { return ""; }

        // Check the cache to see if this is already in there
        final String hash = SecurityUtils.md5Hex(includeAnchors + ":" + in);
        String v = redisClient.work(new Function<Jedis, String>() {
            public String apply(Jedis jedis) {
                return jedis.hget("processed-user-markup-" + includeAnchors, hash);
            }
        });

        if (v != null) { return v; }

        final String newV = doFormatUserMarkup(in, includeAnchors);

        redisClient.work(new Function<Jedis, Void>() {
            public Void apply(Jedis jedis) {
                jedis.hset("processed-user-markup-" + includeAnchors, hash, newV);
                return null;
            }
        });

        return newV;
    }

    private String doFormatUserMarkup(String s, boolean includeAnchors) {
        if (s == null) { return ""; }

        StringBuilder buf = new StringBuilder();

        // Process links
        Matcher m = LINK_PATTERN.matcher(s);
        int i = 0;
        while (m.find()) {
            String url = m.group(1);

            boolean isUrlEnclosedInParens = false;
            boolean doesUrlStartWithParen = false;
            boolean doesUrlEndWithParen = false;

            if (url.startsWith("(") && url.endsWith(")")) {
                url = url.substring(1, url.length()-1);
                isUrlEnclosedInParens = true;
            } else if (url.startsWith("(")) {
                url = url.substring(1);
                doesUrlStartWithParen = true;
            } else if (url.endsWith(")")) {
                if (url.indexOf('(') < 0) {
                    url = url.substring(0, url.length()-1);
                    doesUrlEndWithParen = true;
                }
            }

            buf.append(TextFormat.escapeHtml(s.substring(i, m.start())));

            if (isUrlEnclosedInParens || doesUrlStartWithParen) { buf.append("("); }

            String shortUrl = null;
            if (true/*url.length() <= 24*/) {
                shortUrl = url;
            } else {
                /*
                List<ShortUrl> sus = findAllShortUrlsByLongUrl(url);
                ShortUrl su = null;
                if (sus.isEmpty()) {
                    su = findShortUrlById(createShortUrl(url));
                } else {
                    su = sus.get(0);
                }

                shortUrl = genShortUrl(su.getExternalId());
                */
            }

            String shortUrlName = null;
            if (shortUrl.startsWith("https://")) {
                shortUrlName = shortUrl.substring(8);
            } else {
                shortUrlName = shortUrl.substring(7);
            }

            String title = m.group(2);
            if (isUrlEnclosedInParens || doesUrlEndWithParen) {
                title = title.substring(0, title.length()-1);
            }

            if (includeAnchors) {
                buf.append("<a target=\"_blank\" rel=\"nofollow\" href=\"")
                        .append(shortUrl)
                        .append("\" title=\"")
                        .append(title)
                        .append("\">")
                        .append(shortUrlName)
                        .append("</a>");
            } else {
                buf.append(shortUrlName);
            }

            if (isUrlEnclosedInParens || doesUrlEndWithParen) { buf.append(")"); }

            i = m.end();
        }

        String val = buf.append(TextFormat.escapeHtml(s.substring(i)))
                .toString()
                .replaceAll("\r\n", "<br/>")
                .replaceAll("\n", "<br/>");

        return val;
    }
}
