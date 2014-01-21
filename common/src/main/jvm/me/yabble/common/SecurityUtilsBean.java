package me.yabble.common;

import org.joda.time.LocalDate;

import java.net.URI;
import java.net.URISyntaxException;

public class SecurityUtilsBean {
    private String secret;

    public SecurityUtilsBean(String secret) {
        this.secret = secret;
    }

    public byte[] encrypt(String v) {
        return SecurityUtils.encrypt(v, secret);
    }

    public String encrypt64(String v) {
        return SecurityUtils.base64Encode(SecurityUtils.encrypt(v, secret));
    }

    public String encryptMd5Hex(String v) {
        return SecurityUtils.md5Hex(SecurityUtils.encrypt(v, secret));
    }

    public String encryptSha512Hex(String v) {
        return SecurityUtils.sha512Hex(SecurityUtils.encrypt(v, secret));
    }

    public byte[] encrypt(long v) {
        return SecurityUtils.encrypt(String.valueOf(v), secret);
    }

    public byte[] encrypt(int v) {
        return SecurityUtils.encrypt(String.valueOf(v), secret);
    }

    public String decrypt(byte[] v) {
        return SecurityUtils.decrypt(v, secret);
    }

    public String sign(String uid, String v) {
        String ret = SecurityUtils.base64Encode(
                SecurityUtils.encrypt(String.format("%s-[uid:%s]", v, uid), secret));
        for (int i = 0; i < 64; i++) {
            ret = SecurityUtils.md5Hex(ret);
        }
        return ret;
    }

    public String loginUrl(String userId, String httpUrl) throws URISyntaxException {
        URI uri = new URI(httpUrl);
        LocalDate now = LocalDate.now();
        StringBuilder buf = new StringBuilder();
        buf.append(uri.getScheme()).append("://").append(uri.getHost());
        if (uri.getPort() != -1) {
            buf.append(":").append(uri.getPort());
        }

        String sigPath = String.format("/l%s/%s/%s", uri.getPath(), userId, now.toString());
        String sig = sign(userId, sigPath);

        buf.append(sigPath).append("/").append(sig);

        if (uri.getQuery() != null) {
            buf.append("?").append(uri.getQuery());
        }
        if (uri.getFragment() != null) {
            buf.append("#").append(uri.getFragment());
        }
        return buf.toString();
    }
}
