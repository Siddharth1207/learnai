package com.learnai.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PropertyVideoServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final java.util.concurrent.atomic.AtomicReference<Property> current = new java.util.concurrent.atomic.AtomicReference<>(
            new Property("p1", PropertyFixtures.urbanFlat().normalized(), PropertyStatus.AVAILABLE,
                    PropertySource.FORM, null, null, NOW, null, null));

    @TempDir
    Path directory;

    private PropertyVideoService service() {
        return new PropertyVideoService(properties, directory, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void uploadStoresTheFileAndAddsMetadataToTheListing() throws Exception {
        when(properties.findById("p1")).thenAnswer(inv -> Optional.of(current.get()));
        when(properties.save(any(Property.class))).thenAnswer(inv -> {
            current.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        MockMultipartFile file = new MockMultipartFile("file", "walkthrough.mp4", "video/mp4",
                new byte[] {0, 1, 2, 3});

        PropertyVideo video = service().upload("p1", file);

        assertThat(video.id()).isNotBlank();
        assertThat(video.contentType()).isEqualTo("video/mp4");
        assertThat(video.sizeBytes()).isEqualTo(4);
        assertThat(video.uploadedAt()).isEqualTo(NOW);
        assertThat(video.url()).isEqualTo("/api/properties/p1/videos/" + video.id());
        assertThat(Files.readAllBytes(directory.resolve("p1").resolve(video.id()))).containsExactly(0, 1, 2, 3);
        verify(properties).save(any(Property.class));
    }

    @Test
    void onlyMp4MovAndWebmAreAccepted() {
        when(properties.findById("p1")).thenAnswer(inv -> Optional.of(current.get()));
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> service().upload("p1", file))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(415));
    }

    @Test
    void storedVideoCanBeResolvedAndMissingVideoIsNotFound() throws Exception {
        when(properties.findById("p1")).thenAnswer(inv -> Optional.of(current.get()));
        when(properties.save(any(Property.class))).thenAnswer(inv -> {
            current.set(inv.getArgument(0));
            return inv.getArgument(0);
        });
        PropertyVideo video = service().upload("p1", new MockMultipartFile("file", "clip.webm", "video/webm",
                new byte[] {9, 8}));

        PropertyVideoService.StoredVideo stored = service().get("p1", video.id());

        assertThat(stored.video()).isEqualTo(video);
        assertThat(Files.readAllBytes(stored.path())).containsExactly(9, 8);
        assertThatThrownBy(() -> service().get("p1", "missing"))
                .isInstanceOf(PropertyNotFoundException.class);
    }

    @Test
    void unknownListingIsNotFound() {
        when(properties.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().upload("missing",
                new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[] {1})))
                .isInstanceOf(PropertyNotFoundException.class);
    }
}
