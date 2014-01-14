package me.yabble.common.http.client;

import com.google.common.base.Strings;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HttpUtils {

	public static String mapToQuery(Map<String, String> map, String encoding) {
        List<BasicNameValuePair> nvps = new ArrayList<BasicNameValuePair>();
        
        for (Map.Entry<String, String> e : map.entrySet()) {
            nvps.add(new BasicNameValuePair(e.getKey(), e.getValue()));
        }
        
        return URLEncodedUtils.format(nvps, encoding);
    }
}
