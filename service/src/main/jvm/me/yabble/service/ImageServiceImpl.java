package me.yabble.service;

import me.yabble.common.http.client.GetRequest;
import me.yabble.common.http.client.HttpClient;
import me.yabble.common.http.client.Response;
import me.yabble.common.http.client.ResponseHandler;
import me.yabble.common.wq.WorkQueue;
import me.yabble.common.txn.SpringTransactionSynchronization;
import me.yabble.service.dao.ImageDao;
import me.yabble.service.model.Image;
import me.yabble.service.model.ImageTransform;
import me.yabble.service.proto.ServiceProtos.EntityEvent;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import org.apache.commons.io.IOUtils;

import org.joda.time.DateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.Option;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.apache.commons.io.FileUtils.*;
import static scala.collection.JavaConversions.*;
import static me.yabble.common.Predef.*;

public class ImageServiceImpl implements ImageService {
    private static final Logger log = LoggerFactory.getLogger(ImageServiceImpl.class);

    private static final Map<String, String> MIME_TYPE_TO_FILE_EXTENSION_MAP = ImmutableMap.of(
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/png", "png",
            "image/gif", "gif");

    private static final Map<String, String> DEFAULT_CACHE_METADATA = ImmutableMap.of(
            "Cache-Control", "max-age=604800, public");

    private HttpClient httpClient;
    private ImageDao imageDao;
    private S3Store s3Store;
    private String convertPath;
    private String identifyPath;
    private String defaultProfileImageUrl;
    private WorkQueue workQueue;
    private SpringTransactionSynchronization txnSync;

    public ImageServiceImpl(
            ImageDao imageDao,
            HttpClient httpClient,
            WorkQueue workQueue,
            SpringTransactionSynchronization txnSync,
            S3Store s3Store,
            String convertPath,
            String identifyPath,
            String defaultProfileImageUrl)
    {
        this.httpClient = httpClient;
        this.imageDao = imageDao;
        this.workQueue = workQueue;
        this.txnSync = txnSync;
        this.s3Store = s3Store;
        this.convertPath = convertPath;
        this.identifyPath = identifyPath;
        this.defaultProfileImageUrl = defaultProfileImageUrl;
    }

    @Override
    public String getDefaultProfileImageUrl() {
        return defaultProfileImageUrl;
    }

    @Override
    public Optional<Image.Persisted> optionalByOriginalUrl(String url) {
        List<Image.Persisted> images = seqAsJavaList(imageDao.allByOriginalUrl(url));
        if (images.isEmpty()) {
            return Optional.<Image.Persisted>absent();
        } else {
            return Optional.of(images.get(0));
        }
    }

    @Override
    public Optional<Image.Persisted> optionalByUrl(String url) {
        return option2Optional(imageDao.optionalByUrl(url));
    }

    @Override
    public Optional<Image.Persisted> optionalByUrlOrSecureUrl(String url) {
        if (url.toLowerCase().startsWith("https://")) {
            return optionalBySecureUrl(url);
        } else {
            return optionalByUrl(url);
        }
    }

    @Override
    public Optional<Image.Persisted> optionalBySecureUrl(String url) {
        return option2Optional(imageDao.optionalBySecureUrl(url));
    }

    @Override
    public Image.Persisted getDefaultProfileImage() {
        List<Image.Persisted> images = seqAsJavaList(imageDao.allByOriginalUrl(getDefaultProfileImageUrl()));
        if (images.isEmpty()) {
            String iid = createImageFromUrl(getDefaultProfileImageUrl());
            return imageDao.find(iid);
        } else {
            return images.get(0);
        }
    }

    @Override
    public List<Image.Persisted> all(long offset, long limit) {
        return seqAsJavaList(imageDao.all(offset, limit));
    }

    @Override
    public List<Image.Persisted> allOriginals(long offset, long limit) {
        return seqAsJavaList(imageDao.allOriginals(offset, limit));
    }

    private Optional<String> findExistingImageIdByUrl(String url) {
        if (isInternalUrl(url)) {
            Optional<Image.Persisted> optImage = optionalByUrlOrSecureUrl(url);

            if (optImage.isPresent()) {
                Image.Persisted i = optImage.get();
                while (i.originalImageId().isDefined()) {
                    i = find(i.originalImageId().get());
                }
                return Optional.of(i.id());
            }
        } else {
            Optional<Image.Persisted> optImage = optionalByOriginalUrl(url);
            if (optImage.isPresent()) {
                return Optional.of(optImage.get().id());
            }
        }

        return Optional.<String>absent();
    }

    @Override
    public Optional<String> maybeCreateImageFromUrl(final String url) {
        Optional<String> optExistingId = findExistingImageIdByUrl(url);
        if (optExistingId.isPresent()) {
            return optExistingId;
        }

        String mtv = null;
        byte[] bytes = null;
        try {
            GetRequest get = new GetRequest(url);
            //get.setDoLog(true);
            Map<String, Serializable> m = httpClient.execute(
                    get,
                    new ResponseHandler<Map<String, Serializable>>()
            {
                @Override
                public Map<String, Serializable> handle(Response response) throws Exception {
                    if (response.getStatusCode() >= 400) {
                        throw new RuntimeException(String.format("Unexpected response code [%d] for URL [%s]", response.getStatusCode(), url));
                    }

                    return ImmutableMap.of(
                            "image-data", response.getContentAsBytes(),
                            "mime-type", response.getContentType());
                }
            });
            mtv = contentTypeToMimeType((String) m.get("mime-type"));
            bytes = (byte[]) m.get("image-data");
        } catch (Exception e) {
            log.warn("Could not retrieve image from URL [{}] [{}]", url, e.getMessage());
            return Optional.<String>absent();
        }


        return Optional.of(createImageFromUrl(url, mtv, bytes));
    }

    @Override
    public String createImageFromUrl(String url) {
        Optional<String> optExistingId = findExistingImageIdByUrl(url);
        if (optExistingId.isPresent()) {
            return optExistingId.get();
        }

        try {
            GetRequest get = new GetRequest(url);
            //get.setDoLog(true);
            Map<String, Serializable> m = httpClient.execute(
                    get,
                    new ResponseHandler<Map<String, Serializable>>()
            {
                @Override
                public Map<String, Serializable> handle(Response response) throws Exception {
                    return ImmutableMap.of(
                            "image-data", response.getContentAsBytes(),
                            "mime-type", response.getContentType());
                }
            });

            String mtv = contentTypeToMimeType((String) m.get("mime-type"));

            return createImageFromUrl(url, mtv, (byte[]) m.get("image-data"));
        } catch (ImageException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageException(e);
        }
    }

    private String createImageFromUrl(String url, String mimeType, byte[] bytes) {
        try {
            return createImage(
                    mimeType,
                    Optional.<String>absent(),
                    Optional.of(url),
                    bytes);
        } catch (ImageException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageException(e);
        }
    }

    @Override
    public String createImage(
            String mimeType,
            Optional<String> originalFilename,
            Optional<String> originalUrl,
            byte[] imageData)
    {
        String suffix = mimeTypeToFileExtension(mimeType, originalFilename, originalUrl);

        File in = null;
        File oriented = null;
        try {
            in = File.createTempFile("image-in", "."+suffix);
            oriented = File.createTempFile("image-in-oriented", "."+suffix);
            writeByteArrayToFile(in, imageData);

            autoOrient(in, oriented);

            int imageCount = imageCount(oriented);

            long[] vals = sizeWidthHeight(oriented, imageCount);
            long size = vals[0];
            long width = vals[1];
            long height = vals[2];

            final String id = imageDao.genId();
            String imageName = String.format("%s.%dx%d.%s", id, width, height, suffix);

            InputStream is = null;
            try {
                is = new FileInputStream(oriented);
                s3Store.put(imageName, is, Optional.of(mimeType), DEFAULT_CACHE_METADATA);
            } finally {
                if (is != null) { is.close(); }
            }

            Image.Free f = new Image.Free(
                    Option.apply(id),
                    true,
                    s3Store.getUrl(imageName),
                    s3Store.getSecureUrl(imageName),
                    mimeType,
                    optional2Option(originalFilename),
                    Option.<String>apply(null),
                    Option.apply((Object) size),
                    Option.apply((Object) width),
                    Option.apply((Object) height),
                    Option.<ImageTransform>apply(null),
                    optional2Option(originalUrl));

            String createdId = imageDao.create(f);
            if (!id.equals(createdId)) {
                throw new RuntimeException(String.format("IDs are not equal [%s] [%s]", id, createdId));
            }

            txnSync.add(new Function<Void, Void>() {
                public Void apply(Void ingored) {
                    workQueue.submit(
                            "entity-event",
                            EntityEvent.newBuilder()
                                    .setEntityType(EntityEvent.EntityType.IMAGE)
                                    .setEventType(EntityEvent.EventType.CREATE)
                                    .setEntityId(id)
                                    .setEventTime(DateTime.now().toString())
                                    .build()
                                    .toByteArray());

                    return null;
                }
            });

            return id;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (in != null) { in.delete(); }
            if (oriented != null) { oriented.delete(); }
        }
    }

    @Override
    public boolean maybeSetImagePreviewData(String id) {
        File in = null;
        try {
            Image.Persisted image = imageDao.findForUpdate(id);
            String suffix = mimeTypeToFileExtension(
                    image.mimeType(),
                    option2Optional(image.originalFilename()),
                    option2Optional(image.originalUrl()));

            in = File.createTempFile("image-in.", "."+suffix);
            byte[] bytes = httpClient.executeToBytes(new GetRequest(image.url()));
            writeByteArrayToFile(in, bytes);
            byte[] previewData = previewData(in);
            if (previewData.length < 200) {
                imageDao.updatePreviewData(id, previewData);
                return true;
            } else {
                log.warn("Preview data is too big [{}bytes] for image [{}]", previewData.length, id);
                return false;
            }
        } catch (ImageException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageException(e);
        } finally {
            if (in != null) { in.delete(); }
        }
    }

    private String transformImage(String originalImageId, ImageTransform transform) {
        Image.Persisted original = imageDao.findForUpdate(originalImageId);

        // We've got a lock now, check if the images has been created in the time it took to get the lock
        Optional<Image.Persisted> optImage = option2Optional(imageDao.optionalByOriginalImageAndTransform(originalImageId, transform));
        if (optImage.isPresent()) { return optImage.get().id(); }

        String suffix = "." + mimeTypeToFileExtension(
                original.mimeType(),
                option2Optional(original.originalFilename()),
                option2Optional(original.originalUrl()));

        File in = null;
        File out = null;
        try {
            in = File.createTempFile("image-in.", suffix);
            out = File.createTempFile("image-out.", suffix);

            byte[] originalImageData = httpClient.executeToBytes(new GetRequest(original.url()));
            writeByteArrayToFile(in, originalImageData);

            int imageCount = imageCount(in);

            String cmd = null;
            if (imageCount > 1) { // animaged gif
                switch (transform.getType()) {
                    case RESIZE_BOX:
                        cmd = String.format(
                                "%s %s -coalesce -resize %dx%d> %s",
                                convertPath,
                                in.getAbsolutePath(),
                                transform.getWidth().get(),
                                transform.getHeight().get(),
                                out.getAbsolutePath());
                        break;
                    case RESIZE_WIDTH:
                        cmd = String.format(
                                "%s %s -coalesce -resize %d> %s",
                                convertPath,
                                in.getAbsolutePath(),
                                transform.getWidth().get(),
                                out.getAbsolutePath());
                        break;
                    case RESIZE_HEIGHT:
                        cmd = String.format(
                                "%s %s -coalesce -resize x%d> %s",
                                convertPath,
                                in.getAbsolutePath(),
                                transform.getHeight().get(),
                                out.getAbsolutePath());
                        break;
                    case RESIZE_SQUARE:
                        long w = transform.getWidth().get();
                        cmd = String.format(
                                "%s %s -coalesce -resize %dx%d^ -gravity center -extent %dx%d %s",
                                convertPath,
                                in.getAbsolutePath(),
                                w, w,
                                w, w,
                                out.getAbsolutePath());
                        break;
                    case RESIZE_COVER:
                        w = transform.getWidth().get();
                        long h = transform.getHeight().get();
                        cmd = String.format(
                                "%s %s -coalesce -resize %dx%d^ -gravity center -extent %dx%d %s",
                                convertPath,
                                in.getAbsolutePath(),
                                w, h,
                                w, h,
                                out.getAbsolutePath());
                        break;
                    default:
                        throw new RuntimeException(
                                String.format("Unexpected transform type [%s]", transform.getType()));
                }
            } else {
                switch (transform.getType()) {
                    case RESIZE_BOX:
                        cmd = String.format(
                                "%s %s -resize %dx%d> %s",
                                convertPath,
                                in.getAbsolutePath(),
                                transform.getWidth().get(),
                                transform.getHeight().get(),
                                out.getAbsolutePath());
                        break;
                    case RESIZE_HEIGHT:
                        cmd = String.format(
                                "%s %s -resize x%d> %s",
                                convertPath,
                                in.getAbsolutePath(),
                                transform.getHeight().get(),
                                out.getAbsolutePath());
                        break;
                    case RESIZE_WIDTH:
                        cmd = String.format(
                                "%s %s -resize %d> %s",
                                convertPath,
                                in.getAbsolutePath(),
                                transform.getWidth().get(),
                                out.getAbsolutePath());
                        break;
                    case RESIZE_SQUARE:
                        long w = transform.getWidth().get();
                        cmd = String.format(
                                "%s %s -resize %dx%d^ -gravity center -extent %dx%d %s",
                                convertPath,
                                in.getAbsolutePath(),
                                w, w,
                                w, w,
                                out.getAbsolutePath());
                        break;
                    case RESIZE_COVER:
                        w = transform.getWidth().get();
                        long h = transform.getHeight().get();
                        cmd = String.format(
                                "%s %s -resize %dx%d^ -gravity center -extent %dx%d %s",
                                convertPath,
                                in.getAbsolutePath(),
                                w, h,
                                w, h,
                                out.getAbsolutePath());
                        break;
                    default:
                        throw new RuntimeException(
                                String.format("Unexpected transform type [%s]", transform.getType()));
                }
            }

            log.debug("Running [{}]", cmd);

            run(cmd);

            long[] vals = sizeWidthHeight(out, imageCount);
            long size = vals[0];
            long width = vals[1];
            long height = vals[2];

            String imageName = null;
            final String id = imageDao.genId();
            if (original.originalFilename().isDefined()) {
                imageName = String.format("%s.%s.%dx%d%s", original.originalFilename().get(), id, width, height, suffix);
            } else {
                imageName = String.format("%s.%dx%d%s", id, width, height, suffix);
            }

            InputStream is = null;
            try {
                is = new FileInputStream(out);
                s3Store.put(imageName, is, Optional.of(original.mimeType()), DEFAULT_CACHE_METADATA);
            } finally {
                if (is != null) { is.close(); }
            }

            Image.Free f = new Image.Free(
                    Option.apply(id),
                    true,
                    s3Store.getUrl(imageName),
                    s3Store.getSecureUrl(imageName),
                    original.mimeType(),
                    original.originalFilename(),
                    Option.apply(original.id()),
                    Option.apply((Object) size),
                    Option.apply((Object) width),
                    Option.apply((Object) height),
                    Option.apply(transform),
                    Option.<String>apply(null));

            String createdId = imageDao.create(f);
            if (!id.equals(createdId)) {
                throw new RuntimeException(String.format("IDs are not equal [%s] [%s]", id, createdId));
            }

            txnSync.add(new Function<Void, Void>() {
                public Void apply(Void ingored) {
                    workQueue.submit(
                            "entity-event",
                            EntityEvent.newBuilder()
                                    .setEntityType(EntityEvent.EntityType.IMAGE)
                                    .setEventType(EntityEvent.EventType.CREATE)
                                    .setEntityId(String.valueOf(id))
                                    .setEventTime(DateTime.now().toString())
                                    .build()
                                    .toByteArray());

                    return null;
                }
            });

            return id;
        } catch (RuntimeException e) {
            log.error("Exception transforming image [{}] [{}]", originalImageId, transform);
            throw e;
        } catch (Exception e) {
            log.error("Exception transforming image [{}] [{}]", originalImageId, transform);
            throw new RuntimeException(e);
        } finally {
            if (in != null) { in.delete(); }
            if (out != null) { out.delete(); }
        }
    }

    private String mimeTypeToFileExtension(
            String mimeType,
            Optional<String> optFilename,
            Optional<String> optUrl)
    {
        if (MIME_TYPE_TO_FILE_EXTENSION_MAP.containsKey(mimeType)) {
            return MIME_TYPE_TO_FILE_EXTENSION_MAP.get(mimeType);
        }

        if (optFilename.isPresent()) {
            String filename = optFilename.get().toLowerCase();
            if (filename.indexOf(".jpg") > 0) {
                return "jpg";
            } else if (filename.indexOf(".jpeg") > 0) {
                return "jpg";
            } else if (filename.indexOf(".gif") > 0) {
                return "gif";
            } else if (filename.indexOf(".png") > 0) {
                return "png";
            }
        }

        if (optUrl.isPresent()) {
            String url = optUrl.get().toLowerCase();

            if (url.indexOf(".jpg") > 0) {
                return "jpg";
            } else if (url.indexOf(".jpeg") > 0) {
                return "jpg";
            } else if (url.indexOf(".gif") > 0) {
                return "gif";
            } else if (url.indexOf(".png") > 0) {
                return "png";
            }
        }

        log.warn("Could not find mime type for image [{}] [{}] [{}]", new Object[] {mimeType, optFilename, optUrl});
        throw new ImageService.UnsupportedMimeTypeException(mimeType);
    }

    @Override
    public String mimeTypeToFileExtension(String mimeType) {
        return mimeTypeToFileExtension(mimeType, Optional.<String>absent(), Optional.<String>absent());
    }

    @Override
    public Optional<String> optionalMimeTypeToFileExtension(String mimeType) {
        try {
            return Optional.of(mimeTypeToFileExtension(mimeType, Optional.<String>absent(), Optional.<String>absent()));
        } catch (ImageService.UnsupportedMimeTypeException e) {
            return Optional.<String>absent();
        }
    }

    @Override
    public Optional<Image.Persisted> optional(String id) {
        return option2Optional(imageDao.optional(id));
    }

    @Override
    public Image.Persisted find(String id) {
        return imageDao.find(id);
    }

    @Override
    public Optional<Image.Persisted> optionalByOriginalIdAndTransform(
            String id, String transform)
    {
        return optionalByOriginalIdAndTransform(id, new ImageTransform(transform));
    }

    @Override
    public Optional<Image.Persisted> optionalByOriginalIdAndTransform(
            String id, String transform, boolean doTransform)
    {
        return optionalByOriginalIdAndTransform(
                id, new ImageTransform(transform), doTransform);
    }

    //@Override
    public Optional<Image.Persisted> optionalByOriginalIdAndTransform(
            String id, ImageTransform transform)
    {
        return optionalByOriginalIdAndTransform(id, transform, true);
    }

    //@Override
    public Optional<Image.Persisted> optionalByOriginalIdAndTransform(
            String id, ImageTransform transform, boolean doTransform)
    {
        Optional<Image.Persisted> optOriginal = optional(id);
        if (optOriginal.isPresent()) {
            Image.Persisted original = optOriginal.get();
            Optional<Image.Persisted> optImage = option2Optional(imageDao.optionalByOriginalImageAndTransform(
                    original.id(), transform));
            if (optImage.isPresent()) {
                return optImage;
            } else {
                if (doTransform) {
                    try {
                        String iid = transformImage(original.id(), transform);
                        return option2Optional(imageDao.optional(iid));
                    } catch (Exception e) {
                        // TODO Why catch here? should throw
                        log.error("Error transforming image [{}]: {}", new Object[] {id, e.getMessage(), e});
                        return Optional.<Image.Persisted>absent();
                    }
                } else {
                    return Optional.<Image.Persisted>absent();
                }
            }
        } else {
            return optOriginal;
        }
    }

    // TODO Duplicate code
    @Override
    public Image.Persisted findOrCreateImageByOriginalAndTransform(String id, String transform) {
        return findOrCreateImageByOriginalAndTransform(id, new ImageTransform(transform));
    }

    //@Override
    public Image.Persisted findOrCreateImageByOriginalAndTransform(String id, ImageTransform transform) {
        Optional<Image.Persisted> optImage = option2Optional(imageDao.optionalByOriginalImageAndTransform(id, transform));
        if (optImage.isPresent()) {
            return optImage.get();
        } else {
            return imageDao.find(transformImage(id, transform));
        }
    }

    @Override
    public Optional<Image.Persisted> optionalOrCreateImageByUrlAndTransform(String url, String preset) {
        return findOrCreateImageByUrlAndTransform(url, ImageTransform.parse(preset));
    }

    //@Override
    public Optional<Image.Persisted> findOrCreateImageByUrlAndTransform(String url, ImageTransform transform) {
        Optional<String> optOriginalId = findExistingImageIdByUrl(url);
        if (optOriginalId.isPresent()) {
            String originalId = optOriginalId.get();
            Optional<Image.Persisted> optImage = option2Optional(imageDao.optionalByOriginalImageAndTransform(originalId, transform));
            if (optImage.isPresent()) {
                return optImage;
            } else {
                try {
                    String iid = transformImage(originalId, transform);
                    return option2Optional(imageDao.optional(iid));
                } catch (Exception e) {
                    // TODO Why catch here? should throw
                    log.error("Error transforming image [{}]: {}", new Object[] {originalId, e.getMessage(), e});
                    return Optional.<Image.Persisted>absent();
                }
            }
        } else {
            Optional<String> optId = maybeCreateImageFromUrl(url);
            if (optId.isPresent()) {
                String iid = transformImage(optId.get(), transform);
                return option2Optional(imageDao.optional(iid));
            } else {
                return Optional.<Image.Persisted>absent();
            }
        }
    }

    @Override
    public boolean isInternalUrl(String url) {
        return s3Store.isS3Url(url);
    }

    private String run(String cmd) throws IOException {
        return doRun(Runtime.getRuntime().exec(cmd));
    }

    private String doRun(Process p) throws IOException {
        int ret = 0;
        try {
            ret = p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        if (ret != 0) {
            String err = IOUtils.toString(p.getErrorStream());
            throw new RuntimeException(String.format("Bad return code [%d]: [%s]", ret, err));
        }

        return IOUtils.toString(p.getInputStream());
    }

    private int imageCount(File in) throws IOException {
        String out = run(
                String.format("%s -format %%n %s", identifyPath, in.getAbsolutePath()))
                .trim();

        return Integer.parseInt(out);
    }

    private long[] sizeWidthHeight(File in, int imageCount) throws IOException {
        String out = null;
        if (imageCount > 1) {
            out = run(String.format("%s -format %%b,%%w,%%h %s[0]", identifyPath, in.getAbsolutePath())).trim();
        } else {
            out = run(String.format("%s -format %%b,%%w,%%h %s", identifyPath, in.getAbsolutePath())).trim();
        }

        String[] vals = Iterables.toArray(Splitter.on(',').split(out), String.class);

        long size = Long.parseLong(vals[0].replaceAll("\\D", ""));
        long width = Long.parseLong(vals[1]);
        long height = Long.parseLong(vals[2]);

        return new long[] {size, width, height};
    }

    private void maybeDeleteFromS3(Image.Persisted i) {
        try {
            URL url = new URL(i.url());
            if (url.getHost().indexOf("s3.amazonaws.com") >= 0) {
                String name = url.getFile();
                if (name.startsWith("/")) {
                    name = name.substring(1);
                }
                s3Store.delete(name);
            }
        } catch (MalformedURLException e) {
            log.warn("Malformed URL found when attempting to delete image: {}", e.getMessage(), e);
        }
    }

    private String contentTypeToMimeType(String ct) {
        for (String v : Splitter.on(';').trimResults().split(ct)) {
            if (MIME_TYPE_TO_FILE_EXTENSION_MAP.containsKey(v)) {
                return v;
            }
        }
        return ct;
    }

    private void autoOrient(File in, File out) throws IOException {
        run(String.format("%s %s -auto-orient %s", convertPath, in.getAbsolutePath(), out.getAbsolutePath()));
    }

    private byte[] previewData(File in) {
        File out = null;
        try {
            out = File.createTempFile("image-out.", ".gif");

            String cmd = String.format(
                    "%s %s -strip -sample 4x4 -colors 256 %s",
                    convertPath,
                    in.getAbsolutePath(),
                    out.getAbsolutePath());

            run(cmd);

            return readFileToByteArray(out);
        } catch (ImageException e) {
            throw e;
        } catch (Exception e) {
            throw new ImageException(e);
        } finally {
            if (out != null) { out.delete(); }
        }
    }
}