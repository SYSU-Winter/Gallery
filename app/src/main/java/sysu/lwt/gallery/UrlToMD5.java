package sysu.lwt.gallery;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by 12136 on 2017/3/24.
 */

/**
 * 这个类用于将Url转换成md5, 原因是是图片的url中可能会含有中文字符
 * 等特殊字符，会影响url在Android中的使用
 */
public class UrlToMD5 {
    private String url;
    public UrlToMD5(String url) {
        this.url = url;
    }

    public String hashKeyFromUrl() {
        String cacheKey;
        try {
            // MessageDigest 类为应用程序提供信息摘要算法的功能, 这里是使用MD5
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(url.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(url.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                stringBuilder.append('0');
            }
            stringBuilder.append(hex);
        }
        return stringBuilder.toString();
    }
}
