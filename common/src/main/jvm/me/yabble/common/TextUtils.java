package me.yabble.common;

public class TextUtils {

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
