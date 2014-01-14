package me.yabble.service;

import com.google.common.base.Optional;

import org.jets3t.service.S3Service;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.acl.AccessControlList;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Bucket;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.security.AWSCredentials;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import static java.net.URLEncoder.encode;

public class S3Store {
    private String awsAccessKey;
    private String awsSecretKey;
    private String bucketName;
    private String bucketLocation = S3Bucket.LOCATION_US;

    public S3Store(String awsAccessKey, String awsSecretKey, String bucketName) {
        this.awsAccessKey = awsAccessKey;
        this.awsSecretKey = awsSecretKey;
        this.bucketName = bucketName;
    }

    public void put(
            String name,
            InputStream data,
            Optional<String> optContentType)
    {
        put(name, data, optContentType, null);
    }

    public void put(
            String name,
            InputStream data,
            Optional<String> optContentType,
            Map<String, String> metadata)
    {
        try {
            if (name.startsWith("/")) { name = name.substring(1); }
            RestS3Service s3 = new RestS3Service(new AWSCredentials(awsAccessKey, awsSecretKey));
            S3Bucket bucket = s3.getOrCreateBucket(bucketName, bucketLocation);
            S3Object obj = new S3Object(name);

            if (metadata != null) {
                for (Map.Entry<String, String> e : metadata.entrySet()) {
                    obj.addMetadata(e.getKey(), e.getValue());
                }
            }

            obj.setDataInputStream(data);
            obj.setAcl(AccessControlList.REST_CANNED_PUBLIC_READ);
            if (optContentType.isPresent()) {
                obj.setContentType(optContentType.get());
            }
            s3.putObject(bucket, obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void updateMetadata(
            String name, Optional<String> optContentType, Map<String, String> metadata)
    {
        try {
            if (name.startsWith("/")) { name = name.substring(1); }
            RestS3Service s3 = new RestS3Service(new AWSCredentials(awsAccessKey, awsSecretKey));

            S3Object obj = new S3Object(name);
            obj.setAcl(AccessControlList.REST_CANNED_PUBLIC_READ);

            if (optContentType.isPresent()) {
                obj.setContentType(optContentType.get());
            }

            if (metadata != null) {
                for (Map.Entry<String, String> e : metadata.entrySet()) {
                    obj.addMetadata(e.getKey(), e.getValue());
                }
            }

            s3.copyObject(bucketName, name, bucketName, obj, true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(String name) {
        try {
            if (name.startsWith("/")) { name = name.substring(1); }
            RestS3Service s3 = new RestS3Service(new AWSCredentials(awsAccessKey, awsSecretKey));
            s3.deleteObject(bucketName, name);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream get(String name) {
        try {
            if (name.startsWith("/")) { name = name.substring(1); }
            RestS3Service s3 = new RestS3Service(new AWSCredentials(awsAccessKey, awsSecretKey));
            S3Bucket bucket = s3.getOrCreateBucket(bucketName, bucketLocation);
            try {
                return s3.getObject(bucket, name).getDataInputStream();
            } catch (S3ServiceException e) {
                if (e.getResponseCode() == 404) {
                    throw new RuntimeException(String.format("Resource not found [%s]", name));
                } else {
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getSecureUrl(String name) {
        if (name.startsWith("/")) {
            return "https://s3.amazonaws.com/" + bucketName + encode(name);
        } else {
            return "https://s3.amazonaws.com/" + bucketName + "/" + encode(name);
        }
    }

    public String getUrl(String name) {
        if (name.startsWith("/")) {
            return "http://" + bucketName + ".s3.amazonaws.com" + encode(name);
        } else {
            return "http://" + bucketName + ".s3.amazonaws.com/" + encode(name);
        }
    }

    public String getNameFromUrl(String url) {
        String b = "http://" + bucketName + ".s3.amazonaws.com/";
        return url.substring(b.length());
    }

    public void setBucketLocation(String bucketLocation) {
        this.bucketLocation = bucketLocation;
    }

    public boolean isS3Url(String url) {
        if (url.startsWith("https://")) {
            return url.startsWith("https://s3.amazonaws.com/" + bucketName + "/");
        } else {
            return url.startsWith("http://" + bucketName + ".s3.amazonaws.com/");
        }
    }
}
