package me.yabble.common;

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
}
