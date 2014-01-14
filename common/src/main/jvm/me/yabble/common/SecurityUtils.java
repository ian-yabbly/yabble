package me.yabble.common;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.RandomStringUtils;

import static org.apache.commons.codec.binary.Base64.*;

public class SecurityUtils {

    public static String base64Encode(byte[] bs) {
        return encodeBase64String(bs);
    }

    public static byte[] base64Encode(String s) {
        return decodeBase64(s);
    }

    public static String base64EncodeUrlSafe(byte[] bs) {
        return encodeBase64URLSafeString(bs);
    }

    public static byte[] base64EncodeUrlSafe(String s) {
        return decodeBase64(s);
    }

    public static String utf8Encode(byte[] bs) {
        try {
            return new String(bs, "utf-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] utf8Encode(String s) {
        try {
            return s.getBytes("utf-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String iso88591Encode(byte[] bs) {
        try {
            return new String(bs, "iso-8859-1");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] iso88591Encode(String s) {
        try {
            return s.getBytes("iso-8859-1");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String md5Hex(String s) {
        return DigestUtils.md5Hex(s);
    }

    public static String md5Hex(byte[] bs) {
        return DigestUtils.md5Hex(bs);
    }

    public static String sha256Hex(String s) {
        return DigestUtils.sha256Hex(s);
    }

    public static String sha256Hex(byte[] bs) {
        return DigestUtils.sha256Hex(bs);
    }

    public static String sha512Hex(String s) {
        return DigestUtils.sha512Hex(s);
    }

    public static String sha512Hex(byte[] bs) {
        return DigestUtils.sha512Hex(bs);
    }

    public static String randomAlphabetic(int length) {
        return RandomStringUtils.randomAlphabetic(length);
    }

    public static String randomAlphanumeric(int length) {
        return RandomStringUtils.randomAlphanumeric(length);
    }

    public static String randomNumeric(int length) {
        return RandomStringUtils.randomNumeric(length);
    }
}
