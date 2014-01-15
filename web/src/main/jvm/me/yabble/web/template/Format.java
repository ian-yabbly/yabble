package me.yabble.web.template;

import me.yabble.common.TextFormat;
import me.yabble.common.TextUtils;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import org.springframework.web.util.UriComponentsBuilder;

import org.joda.time.DateTimeZone;

import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang.WordUtils.capitalizeFully;
import static java.util.concurrent.TimeUnit.*;

public class Format {
    private static final DateTimeZone DEFAULT_TZ = DateTimeZone.forID("US/Pacific");
    private static final DateTimeFormatter normalDF = DateTimeFormat.forPattern("MMM d, yyyy h:mm a");
    private static final DateTimeFormatter w3cYmdDF = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter ymdDF = DateTimeFormat.forPattern("MMM d, yyyy");

    private static final Pattern LINK_PATTERN = Pattern.compile("(\\(?\\bhttps?://([-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]))");

    private static final long ONE_MINUTE_MS = 60l*1000l;
    private static final long ONE_HOUR_MS = 60l*ONE_MINUTE_MS;
    private static final long ONE_DAY_MS = 24l*ONE_HOUR_MS;
    private static final long ONE_WEEK_MS = 7l*ONE_DAY_MS;
    private static final long ONE_MONTH_MS = 30l*ONE_DAY_MS;
    private static final long ONE_YEAR_MS = 365l*ONE_DAY_MS;

    public static String shortFormat(DateTime t) {
        if (t == null) { return ""; }
        return DateTimeFormat.shortDateTime().print(t);
    }

    public static String localDate(DateTime t, Optional<DateTimeZone> optDtz) {
        if (t == null) { return ""; }
        if (optDtz == null) {
            return localDate(t.toLocalDate());
        } else if (optDtz.isPresent()) {
            return localDate(new LocalDate(t, optDtz.get()));
        } else {
            return localDate(t.toLocalDate());
        }
    }

    public static String localDate(LocalDate t) {
        if (t == null) { return ""; }
        return ymdDF.print(t);
    }

    public static String timeAgo(DateTime t, Optional<DateTimeZone> optTz) {
        if (t == null) { return ""; }

        long msDiff = DateTime.now().getMillis() - t.getMillis();

        if (msDiff < 50*1000) {
            return "seconds ago";
        } else if (msDiff < 50l*ONE_MINUTE_MS) {
            return "minutes ago";
        } else if (msDiff < 20l*ONE_HOUR_MS) { // n hours ago
            long hours = msDiff / ONE_HOUR_MS;
            //long rem = (msDiff % ONE_HOUR_MS) / ONE_MINUTE_MS;
            if (hours <= 1) {
                return "one hour ago";
            } else {
                return TextUtils.numberToText(hours) + " hours ago";
            }
        } else if (msDiff < 7l*ONE_DAY_MS) { // n days ago
            long days = msDiff / ONE_DAY_MS;
            if (days <= 1) {
                return "one day ago";
            } else {
                return TextUtils.numberToText(days) + " days ago";
            }
        } else if (msDiff < 4l*ONE_WEEK_MS) { // n weeks ago
            long weeks = msDiff / ONE_WEEK_MS;
            if (weeks <= 1) {
                return "one week ago";
            } else {
                return TextUtils.numberToText(weeks) + " weeks ago";
            }
        } else if (msDiff < 12l*ONE_MONTH_MS) { // n months ago
            long months = msDiff / ONE_MONTH_MS;
            if (months <= 1) {
                return "one month ago";
            } else {
                return TextUtils.numberToText(months) + " months ago";
            }
        } else { // n years ago
            long years = msDiff / ONE_YEAR_MS;
            if (years <= 1) {
                return "one year ago";
            } else {
                return TextUtils.numberToText(years) + " years ago";
            }
        }
    }

    public static String strTimeAgo(String t, Optional<DateTimeZone> optTz) {
        return timeAgo(new DateTime(t), optTz);
    }

    public static String format(Enum e) {
        return TextUtils.enumToString(e);
    }

    public static String dayFormat(DateTime t, Optional<DateTimeZone> optTz) {
        if (optTz != null && optTz.isPresent()) {
            return ymdDF.print(t.withZone(optTz.get()));
        } else {
            return ymdDF.print(t.withZone(DEFAULT_TZ));
        }
    }

    public static String dayFormat(Timestamp t, Optional<DateTimeZone> optTz) {
        DateTime dt = new DateTime(t.getTime());
        if (optTz != null && optTz.isPresent()) {
            return ymdDF.print(dt.withZone(optTz.get()));
        } else {
            return ymdDF.print(dt.withZone(DEFAULT_TZ));
        }
    }

    public static String format(Timestamp t, Optional<DateTimeZone> optTz) {
        return format(new DateTime(t.getTime()), optTz);
    }

    public static String format(DateTime t, Optional<DateTimeZone> optTz) {
        if (t == null) { return ""; }

        if (optTz != null && optTz.isPresent()) {
            return format(t.withZone(optTz.get()));
        } else {
            return format(t.withZone(DEFAULT_TZ));
        }
    }

    public static String format(LocalDateTime t) {
        if (t == null) { return ""; }
        return normalDF.print(t);
    }

    public static String format(DateTime t) {
        if (t == null) { return ""; }
        return normalDF.print(t);
    }

    public static String format(Date d) {
        if (d == null) { return ""; }
        return format(new DateTime(d));
    }

    public static String formatW3cYmd(DateTime t) {
        if (t == null) { return ""; }
        return w3cYmdDF.print(t);
    }

    public static String escapeHtml(Object o) {
        if (o == null) { return ""; }
        return escapeHtml(o.toString());
    }

    public static String escapeHtml(String s) {
        return TextFormat.escapeHtml(s);
    }

    public static String formatAsRelativeDuration(DateTime t) {
        if (t == null) { return ""; }
        DateTime now = DateTime.now();

        long msDiff = Math.abs(now.getMillis() - t.getMillis());

        if (MILLISECONDS.toDays(msDiff) >= 2l) {
            return String.format("%d days", MILLISECONDS.toDays(msDiff));
        } else if (MILLISECONDS.toHours(msDiff) < 48l && MILLISECONDS.toHours(msDiff) >= 1l) {
            return String.format("%d hours", MILLISECONDS.toHours(msDiff));
        } else {
            return String.format("%d minutes", MILLISECONDS.toMinutes(msDiff) + 1l);
        }
    }

    public static String join(String sep, List vs) {
        if (vs == null) { return ""; }
        return Joiner.on(sep).join(vs);
    }

    public static String pageTitle(String v) {
        if (v == null) { return ""; }
        if (v.length() <= 140) { return escapeHtml(v); }
        return escapeHtml(v.substring(0, 140));
    }

    public static String maybeTruncate(String v, int maxLength, Optional<String> optTruncateMarker) {
        if (v.length() > maxLength) {
            if (optTruncateMarker.isPresent()) {
                return v.substring(0, maxLength) + optTruncateMarker.get();
            } else {
                return v.substring(0, maxLength);
            }
        } else {
            return v;
        }
    }

    public static String urlEncode(String v) {
        if (v == null) { return ""; }
        return URLEncoder.encode(v);
    }

    public static String addQueryParam(String url, String name, String value) {
        return UriComponentsBuilder
                .fromHttpUrl(url)
                .queryParam(name, URLEncoder.encode(value))
                .build()
                .toUriString();
    }

    public static Optional<Object> makeOptional(Object o) {
        return Optional.fromNullable(o);
    }

    public static DateTime dateTimeFromMillis(long millis) {
        return new DateTime(millis);
    }

    public static int randomPositiveInteger(int min, int max) {
        return min + ((int) (Math.random()*(max-min+1)));
    }
}
