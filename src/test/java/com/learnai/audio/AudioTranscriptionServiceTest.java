package com.learnai.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

class AudioTranscriptionServiceTest {

    private static final InputLanguage HINDI = InputLanguage.resolve("hi").orElseThrow();

    @TempDir
    Path outputDir;

    private static final MimeType MP3 = MimeType.valueOf("audio/mpeg");

    private final SpeechToText model = mock(SpeechToText.class);
    private final TextTranslator translator = mock(TextTranslator.class);
    private final Resource audio = new ByteArrayResource(new byte[] {1, 2, 3});

    @Test
    void translatesNonEnglishAudioAndSavesEnglishText() throws Exception {
        when(model.transcribe(audio, MP3, HINDI)).thenReturn(" नमस्ते दुनिया ");
        when(translator.translateToEnglish("नमस्ते दुनिया", HINDI)).thenReturn("Hello world");

        TranscriptionResult result = service().transcribe(audio, MP3, HINDI);

        assertThat(result.transcript()).isEqualTo("नमस्ते दुनिया");
        assertThat(result.translated()).isTrue();
        assertThat(result.englishText()).isEqualTo("Hello world");
        assertThat(Files.readString(Path.of(result.savedFile()), StandardCharsets.UTF_8)).isEqualTo("Hello world");
        assertThat(Path.of(result.savedFile()).getParent()).isEqualTo(outputDir);
    }

    @Test
    void savesEnglishAudioWithoutTranslation() throws Exception {
        when(model.transcribe(eq(audio), eq(MP3), any())).thenReturn("Hello world");

        TranscriptionResult result = service().transcribe(audio, MP3, InputLanguage.ENGLISH);

        assertThat(result.translated()).isFalse();
        assertThat(Files.readString(Path.of(result.savedFile()))).isEqualTo("Hello world");
        verifyNoInteractions(translator);
    }

    @Test
    void eachCallWritesANewFile() {
        when(model.transcribe(eq(audio), eq(MP3), any())).thenReturn("Hello");
        AudioTranscriptionService service = service();

        assertThat(service.transcribe(audio, MP3, InputLanguage.ENGLISH).savedFile())
                .isNotEqualTo(service.transcribe(audio, MP3, InputLanguage.ENGLISH).savedFile());
    }

    @Test
    void skipsTranslationForSilentAudio() {
        when(model.transcribe(eq(audio), eq(MP3), any())).thenReturn("  ");

        TranscriptionResult result = service().transcribe(audio, MP3, HINDI);

        assertThat(result.translated()).isFalse();
        assertThat(result.englishText()).isEmpty();
        verifyNoInteractions(translator);
    }

    private AudioTranscriptionService service() {
        return new AudioTranscriptionService(model, translator, outputDir);
    }
}
