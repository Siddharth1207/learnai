package com.learnai.audio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

class AudioTranscriptionServiceTest {

    private static final InputLanguage HINDI = InputLanguage.resolve("hi").orElseThrow();

    @TempDir
    Path outputDir;

    private final TranscriptionModel model = mock(TranscriptionModel.class);
    private final TextTranslator translator = mock(TextTranslator.class);
    private final Resource audio = new ByteArrayResource(new byte[] {1, 2, 3});

    @Test
    void translatesNonEnglishAudioAndSavesEnglishText() throws Exception {
        when(model.transcribe(eq(audio), argThat(o -> o instanceof OpenAiAudioTranscriptionOptions opts && "hi".equals(opts.getLanguage())))).thenReturn(" नमस्ते दुनिया ");
        when(translator.translateToEnglish("नमस्ते दुनिया", HINDI)).thenReturn("Hello world");

        TranscriptionResult result = service(model).transcribe(audio, HINDI);

        assertThat(result.transcript()).isEqualTo("नमस्ते दुनिया");
        assertThat(result.translated()).isTrue();
        assertThat(result.englishText()).isEqualTo("Hello world");
        assertThat(Files.readString(Path.of(result.savedFile()), StandardCharsets.UTF_8)).isEqualTo("Hello world");
        assertThat(Path.of(result.savedFile()).getParent()).isEqualTo(outputDir);
    }

    @Test
    void savesEnglishAudioWithoutTranslation() throws Exception {
        when(model.transcribe(eq(audio), any())).thenReturn("Hello world");

        TranscriptionResult result = service(model).transcribe(audio, InputLanguage.ENGLISH);

        assertThat(result.translated()).isFalse();
        assertThat(Files.readString(Path.of(result.savedFile()))).isEqualTo("Hello world");
        verifyNoInteractions(translator);
    }

    @Test
    void eachCallWritesANewFile() {
        when(model.transcribe(eq(audio), any())).thenReturn("Hello");
        AudioTranscriptionService service = service(model);

        assertThat(service.transcribe(audio, InputLanguage.ENGLISH).savedFile())
                .isNotEqualTo(service.transcribe(audio, InputLanguage.ENGLISH).savedFile());
    }

    @Test
    void skipsTranslationForSilentAudio() {
        when(model.transcribe(eq(audio), any())).thenReturn("  ");

        TranscriptionResult result = service(model).transcribe(audio, HINDI);

        assertThat(result.translated()).isFalse();
        assertThat(result.englishText()).isEmpty();
        verifyNoInteractions(translator);
    }

    @Test
    void failsWhenNoTranscriptionModelConfigured() {
        assertThatThrownBy(() -> service(null).transcribe(audio, HINDI))
                .isInstanceOf(AiNotConfiguredException.class);
    }

    private AudioTranscriptionService service(TranscriptionModel transcriptionModel) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory(
                transcriptionModel == null ? Map.of() : Map.of("transcriptionModel", transcriptionModel));
        return new AudioTranscriptionService(beans.getBeanProvider(TranscriptionModel.class), translator, outputDir);
    }
}
