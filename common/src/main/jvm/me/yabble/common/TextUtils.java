package me.yabble.common;

import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import org.springframework.web.util.UriComponentsBuilder;

import scala.Option;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang.WordUtils.capitalizeFully;

public class TextUtils {
    public static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\w+");

    public static String replaceNewlinesWithSpace(String v) {
        return v.replaceAll("\\r\\n|\\r|\\n", " ");
    }

    public static String enumToCode(Enum e) {
        if (e == null) { return null; }
        return stringToCode(e.name());
    }       

    public static String stringToCode(String v) {
        if (v == null) { return null; }
        return replaceStartChars(v.replaceAll("_", "-").toLowerCase(), '-', '_');
    }       

    public static <T extends Enum> T codeToEnum(String name, T fallback) {
        if (name == null) { return null; }
        try {
            return (T) T.valueOf(fallback.getClass(), name.replaceAll("-", "_").toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    public static <T extends Enum> T codeToEnum(String name, Class<T> c) {
        if (name == null) { return null; }
        return (T) T.valueOf(c, name.replaceAll("-", "_").toUpperCase());
    }

    public static String enumToString(Enum e) {
        if (e == null) { return null; }
        return capitalizeFully(e.name().replaceAll("_", " "));
    }

    public static int wordCount(String s) {
        if (s == null) { return 0; }
        return WHITESPACE_PATTERN.split(s).length;
    }

    public static String firstNWords(String s, int n) {
        StringBuilder buf = new StringBuilder();
        Matcher m = WHITESPACE_PATTERN.matcher(s);

        int startOfThisMatch = 0;
        int endOfPrevMatch = 0;
        for (int i = 0; i < n && m.find(); i++) {
            startOfThisMatch = m.start();
            // Append the preceding whitespace
            buf.append(s.substring(endOfPrevMatch, startOfThisMatch));
            buf.append(m.group());
            endOfPrevMatch = m.end();
        }

        return buf.toString();
    }

    public static String firstNChars(Option<String> o, int c) {
        if (o.isDefined()) {
            return firstNChars(o.get(), c);
        }
        return "";
    }

    public static String firstNChars(String s, int c) {
        if (s == null) { return ""; }

        assert(c < 4);

        if (s.length() <= c) { return s; }

        StringBuilder buf = new StringBuilder();
        Matcher m = WHITESPACE_PATTERN.matcher(s);

        int startOfThisMatch = 0;
        int endOfPrevMatch = 0;
        int n = c - 4;
        int lc = 0;
        while (m.find()) {
            startOfThisMatch = m.start();
            int wslen = startOfThisMatch - endOfPrevMatch;
            String group = m.group();
            if ((lc + wslen + group.length()) > n) {
                break;
            } else {
                buf.append(s.substring(endOfPrevMatch, startOfThisMatch));
                buf.append(group);
                endOfPrevMatch = m.end();
                lc += wslen;
                lc += group.length();
            }
        }
        buf.append("...");

        return buf.toString();
    }

    public static String numberToText(Number n) {
        int v = n.intValue();

        if (v == 0) {
            return "zero";
        } else if (v == 1) {
            return "one";
        } else if (v == 2) {
            return "two";
        } else if (v == 3) {
            return "three";
        } else if (v == 4) {
            return "four";
        } else if (v == 5) {
            return "five";
        } else if (v == 6) {
            return "six";
        } else if (v == 7) {
            return "seven";
        } else if (v == 8) {
            return "eight";
        } else if (v == 9) {
            return "nine";
        } else {
            return String.valueOf(n);
        }
    }

    public static String numberToOrdinal(Number n) {
        long v = n.longValue();
        String strVal = String.valueOf(v);
        int lastDigit = Integer.parseInt(String.valueOf(strVal.charAt(strVal.length()-1)));

        if (lastDigit == 1) {
            return v + "st";
        } else if (lastDigit == 2) {
            return v + "nd";
        } else if (lastDigit == 3) {
            return v + "rd";
        } else {
            return v + "th";
        }
    }

    public static int versionCompare(String v1, String v2) {
        if (v1 == null && v2 == null) {
            return 0;
        } else if (v1 == null) {
            return -1;
        } else if (v2 == null) {
            return 1;
        }

        List<Integer> v1s = Lists.newArrayList();
        List<Integer> v2s = Lists.newArrayList();

        for (String v : v1.split("\\.")) {
            v1s.add(Integer.parseInt(v));
        }

        for (String v : v2.split("\\.")) {
            v2s.add(Integer.parseInt(v));
        }

        int i = 0;
        while (i < v1s.size() && i < v2s.size()) {
            int v1v = v1s.get(i);
            int v2v = v2s.get(i);
            if (v1v < v2v) {
                return -1;
            } else if (v1v > v2v) {
                return 1;
            }
            i++;
        }

        if (v1s.size() == v2s.size()) {
            return 0;
        } else if (v1s.size() < v2s.size()) {
            return -1;
        } else {
            return 1;
        }
    }

    public static String removePrefix(String s, String... prefixes) {
        for (String p : prefixes) {
            if (s.startsWith(p)) {
                return s.substring(p.length());
            }
        }
        return s;
    }

    public static String urlEncode(String s, String charset) {
        try {
            return URLEncoder.encode(s, charset);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String urlEncode(String s) {
        return URLEncoder.encode(s);
    }

    public static String urlDecode(String s) {
        return URLDecoder.decode(s);
    }

    public static String addQueryParamsToHttpUrl(String httpUrl, String name, String value) {
        Optional optFrag = urlFragment(httpUrl);
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(stripUrlFragment(httpUrl));
        b.queryParam(name, value);

        if (optFrag.isPresent()) {
            return b.build().toUriString() + optFrag.get();
        } else {
            return b.build().toUriString();
        }
    }

    public static String addQueryParamsToHttpUrl(String httpUrl, Map<String, String> params) {
        Optional optFrag = urlFragment(httpUrl);
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(stripUrlFragment(httpUrl));
        for (Map.Entry<String, String> e : params.entrySet()) {
            b.queryParam(e.getKey(), e.getValue());
        }

        if (optFrag.isPresent()) {
            return b.build().toUriString() + optFrag.get();
        } else {
            return b.build().toUriString();
        }
    }

    public static String removeUrlQueryAndFragment(String url) {
        if (url.indexOf('?') >= 0) {
            return url.substring(0, url.indexOf('?'));
        } else {
            return url;
        }
    }

    public static Optional<String> getUrlQuery(String url) {
        String t = stripUrlFragment(url);
        if (t.indexOf('?') >= 0) {
            return Optional.of(t.substring(t.indexOf('?')));
        } else {
            return Optional.<String>absent();
        }
    }

    public static Optional<String> urlFragment(String url) {
        int i = url.indexOf('#');
        if (i >= 0) {
            return Optional.of(url.substring(i));
        } else {
            return Optional.<String>absent();
        }
    }

    public static String stripUrlFragment(String httpUrl) {
        int i = httpUrl.indexOf('#');
        if (i >= 0) {
            return httpUrl.substring(0, i);
        } else {
            return httpUrl;
        }
    }

    public static String capitalizeFirstLetter(String s) {
        if(s == null || s.length() == 0) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String replaceStartChars(String v, char find, char replace) {
        if (v != null && v.length() > 0 && v.charAt(0) == find) {
            StringBuilder buf = new StringBuilder();
            int i = 0;
            while (v.charAt(i) == find) {
                buf.append(replace);
                i++;
            }
            buf.append(v.substring(i));
            return buf.toString(); 
        } else {
            return v;
        }
    }
}
