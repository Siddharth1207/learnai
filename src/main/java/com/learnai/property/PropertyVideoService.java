package com.learnai.property;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Stores walkthrough videos on disk and keeps the metadata on the listing.
 * Files live in {@code learnai.videos.dir} under {@code <propertyId>/<videoId>} and are served by the controller.
 */
@Service
public class PropertyVideoService {

    static final long MAX_VIDEO_BYTES = 50L * 1024 * 1024;
    private static final List<String> ALLOWED_TYPES = List.of("video/mp4", "video/quicktime", "video/webm");

    private static final Logger log = LoggerFactory.getLogger(PropertyVideoService.class);

    private final PropertyRepository properties;
    private final Path directory;
    private final Clock clock;

    @Autowired
    public PropertyVideoService(PropertyRepository properties,
            @Value("${learnai.videos.dir:./videos}") String directory) {
        this(properties, Path.of(directory), Clock.systemUTC());
    }

    PropertyVideoService(PropertyRepository properties, Path directory, Clock clock) {
        this.properties = properties;
        this.directory = directory;
        this.clock = clock;
    }

    /** Uploads a video for an existing listing and returns its metadata. */
    public PropertyVideo upload(String propertyId, MultipartFile file) {
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property " + propertyId + " not found"));
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Video file is empty");
        }
        if (file.getSize() > MAX_VIDEO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Video must be 50 MB or smaller");
        }
        String contentType = videoContentType(file);
        String videoId = UUID.randomUUID().toString();
        Path target = directory.resolve(propertyId).resolve(videoId);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the video", e);
        }

        PropertyVideo video = new PropertyVideo(videoId, "/api/properties/%s/videos/%s".formatted(propertyId, videoId),
                contentType, file.getSize(), clock.instant());
        List<PropertyVideo> videos = new ArrayList<>(property.videos() == null ? List.of() : property.videos());
        videos.add(video);
        try {
            properties.save(property.withVideos(videos));
        }
        catch (RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            }
            catch (IOException cleanup) {
                log.warn("Could not delete orphaned video {}: {}", target, cleanup.getMessage());
            }
            throw e;
        }
        return video;
    }

    /** Resolves a stored video for streaming. */
    public StoredVideo get(String propertyId, String videoId) {
        Property property = properties.findById(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property " + propertyId + " not found"));
        PropertyVideo video = Optional.ofNullable(property.videos()).orElse(List.of()).stream()
                .filter(v -> v.id().equals(videoId))
                .findFirst()
                .orElseThrow(() -> new PropertyNotFoundException("Video " + videoId + " not found"));
        Path file = directory.resolve(propertyId).resolve(videoId);
        if (!Files.isRegularFile(file)) {
            throw new PropertyNotFoundException("Video " + videoId + " not found");
        }
        return new StoredVideo(file, video);
    }

    public record StoredVideo(Path path, PropertyVideo video) {
    }

    private static String videoContentType(MultipartFile file) {
        MimeType mimeType = Optional.ofNullable(file.getContentType())
                .map(PropertyVideoService::parseQuietly)
                .filter(type -> ALLOWED_TYPES.contains(type.toString()))
                .or(() -> MediaTypeFactory.getMediaType(file.getOriginalFilename()))
                .filter(type -> ALLOWED_TYPES.contains(type.toString()))
                .orElse(null);
        if (mimeType == null) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Video must be MP4, MOV or WebM");
        }
        return mimeType.toString().toLowerCase(Locale.ROOT);
    }

    private static MimeType parseQuietly(String value) {
        try {
            return MimeTypeUtils.parseMimeType(value);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }
}
