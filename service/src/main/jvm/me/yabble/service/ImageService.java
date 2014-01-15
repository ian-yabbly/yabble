package me.yabble.service;

import me.yabble.service.model.Dimensions;
import me.yabble.service.model.Image;

import com.google.common.base.Optional;

import java.io.IOException;
import java.util.List;

public interface ImageService {
    List<Image.Persisted> all(long offset, long limit);
    List<Image.Persisted> allOriginals(long offset, long limit);
    Image.Persisted find(String id);
    Optional<Image.Persisted> optional(String id);

    Optional<Image.Persisted> optionalByOriginalUrl(String url);

    Optional<Image.Persisted> optionalByUrl(String url);

    Optional<Image.Persisted> optionalByUrlOrSecureUrl(String url);

    Optional<Image.Persisted> optionalBySecureUrl(String url);

    Optional<Image.Persisted> optionalByOriginalIdAndTransform(
            String id, String transform);

    Optional<Image.Persisted> optionalByOriginalIdAndTransform(
            String id, String transform, boolean doTransform);


    Image.Persisted findOrCreateImageByOriginalAndTransform(String id, String transform);

    Optional<Image.Persisted> optionalOrCreateImageByUrlAndTransform(String url, String preset);

    String getDefaultProfileImageUrl();

    Image.Persisted getDefaultProfileImage();

    Optional<String> maybeCreateImageFromUrl(String url);

    String createImageFromUrl(String url) throws UnsupportedMimeTypeException;

    boolean isInternalUrl(String url);

    String createImage(
            String mimeType,
            Optional<String> originalFilename,
            Optional<String> originalUrl,
            byte[] imageData)
        throws UnsupportedMimeTypeException;

    String mimeTypeToFileExtension(String mimeType);
    Optional<String> optionalMimeTypeToFileExtension(String mimeType);

    boolean maybeSetImagePreviewData(String id);

    Dimensions getDimensionsByImageAndTransform(String id, String transform);

    public static class ImageException extends RuntimeException {
        public ImageException(String message) {
            super(message);
        }

        public ImageException(Throwable cause) {
            super(cause);
        }
    }

    public static class UnsupportedMimeTypeException extends ImageException {
        private String mimeType;
        public UnsupportedMimeTypeException(String mimeType) {
            super(mimeType);
            this.mimeType = mimeType;
        }
        public String getMimeType() { return mimeType; }
    }
}
