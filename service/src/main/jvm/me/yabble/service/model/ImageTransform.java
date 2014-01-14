package me.yabble.service.model;

import com.google.common.base.Optional;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ImageTransform {
    private static final Pattern BOX_PATTERN = Pattern.compile("^b-(\\d+)x(\\d+)$");
    private static final Pattern WIDTH_PATTERN = Pattern.compile("^w-(\\d+)$");
    private static final Pattern HEIGHT_PATTERN = Pattern.compile("^h-(\\d+)$");
    private static final Pattern SQUARE_PATTERN = Pattern.compile("^s-(\\d+)$");
    private static final Pattern COVER_PATTERN = Pattern.compile("^c-(\\d+)x(\\d+)$");

    public static ImageTransform parse(String preset) {
        return new ImageTransform(preset);
    }

    public static enum Type { RESIZE_BOX, RESIZE_WIDTH, RESIZE_HEIGHT, RESIZE_SQUARE, RESIZE_COVER; }

    private Type type;
    private Optional<Long> width;
    private Optional<Long> height;

    public ImageTransform(String t) {
        Matcher m = null;
        m = BOX_PATTERN.matcher(t);
        if (m.matches()) {
            type = Type.RESIZE_BOX;
            width = Optional.of(Long.parseLong(m.group(1)));
            height = Optional.of(Long.parseLong(m.group(2)));
        } else {
            m = WIDTH_PATTERN.matcher(t);
            if (m.matches()) {
                type = Type.RESIZE_WIDTH;
                width = Optional.of(Long.parseLong(m.group(1)));
                height = Optional.<Long>absent();
            } else {
                m = SQUARE_PATTERN.matcher(t);
                if (m.matches()) {
                    type = Type.RESIZE_SQUARE;
                    width = Optional.of(Long.parseLong(m.group(1)));
                    height = Optional.<Long>absent();
                } else {
                    m = HEIGHT_PATTERN.matcher(t);
                    if (m.matches()) {
                        type = Type.RESIZE_HEIGHT;
                        width = Optional.<Long>absent();
                        height = Optional.of(Long.parseLong(m.group(1)));
                    } else{
                        m = COVER_PATTERN.matcher(t);
                        if (m.matches()) {
                            type = Type.RESIZE_COVER;
                            width = Optional.of(Long.parseLong(m.group(1)));
                            height = Optional.of(Long.parseLong(m.group(2)));
                        } else {
                            throw new RuntimeException(
                                    String.format("Malformed ImageTransform descriptor [%s]", t));
                        }
                    }
                }
            }
        }
    }

    public ImageTransform(Type type, Optional<Long> width, Optional<Long> height) {
        this.type = type;
        this.width = width;
        this.height = height;
    }

    public Type getType() { return type; }
    public Optional<Long> getWidth() { return width; }
    public Optional<Long> getHeight() { return height; }
}
