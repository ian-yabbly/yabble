package me.yabble.common;

import com.google.common.collect.Iterables;

import org.apache.velocity.tools.generic.EscapeTool;

import java.util.Iterator;

public class TextFormat {
    private static final EscapeTool esc = new EscapeTool();

    public static String toPrintList(Iterable iter) {
        if (iter == null) { return ""; }

        Object[] vals = Iterables.toArray(iter, Object.class);

        if (vals.length == 0) {
            return "";
        } else if (vals.length == 1) {
            return vals[0].toString();
        } else if (vals.length == 2) {
            return String.format("%s and %s", vals[0].toString(), vals[1].toString());
        } else {
            StringBuilder buf = new StringBuilder();
            for (int i = 0; i < vals.length; i++) {
                if (i > 0) { buf.append(", "); }
                if (i == (vals.length-1)) { buf.append("and "); }
                buf.append(vals[i].toString());
            }
            return buf.toString();
        }
    }

    public static String escapeHtml(String s) {
        if (s == null) { return ""; }
        return esc.html(s);
    }
}
