package me.yabble.common.http.client;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;

/**
 * @author Brian J. Gebala
 * @version 1/17/12 10:20 AM
 */
public class HttpJsonCLI {
    public static void main(final String[] args) {
        String url = args[0];
        String jsonFile = args[1];

        File file = new File(jsonFile);
        FileEntity entity = new FileEntity(file, "application/json; charset=\"UTF-8\"");
        
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);
        post.setEntity(entity);
        
        try {
            HttpResponse response = client.execute(post);
            String responseString = EntityUtils.toString(response.getEntity());
            System.out.println(responseString);
        }
        catch (Throwable thr) {
            thr.printStackTrace();
        }
    }
}
