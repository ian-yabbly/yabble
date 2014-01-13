package me.yabble.web.template;

import org.apache.commons.io.FilenameUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Utils {
    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static String normalizePath(String path) {
        String n = FilenameUtils.normalize(path);
        return n;
    }
}
