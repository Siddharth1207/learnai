package com.learnai.audio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

/**
 * Converts speech to text and, for non-English audio, translates the transcript to English.
 * The English text is written to a new file in {@code learnai.audio.output-dir}.
 */
@Service
public class AudioTranscriptionService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final SpeechToText speechToText;
    private final TextTranslator translator;
    private final Path outputDir;

    public AudioTranscriptionService(SpeechToText speechToText, TextTranslator translator,
            @Value("${learnai.audio.output-dir:transcripts}") Path outputDir) {
        this.speechToText = speechToText;
        this.translator = translator;
        this.outputDir = outputDir;
    }

    public TranscriptionResult transcribe(Resource audio, MimeType mimeType, InputLanguage language) {
        String transcript = speechToText.transcribe(audio, mimeType, language).trim();

        boolean translate = !language.isEnglish() && !transcript.isEmpty();
        String englishText = translate ? translator.translateToEnglish(transcript, language) : transcript;

        Path savedFile = save(englishText);
        return new TranscriptionResult(language.code(), transcript, translate, englishText, savedFile.toString());
    }

    private Path save(String text) {
        try {
            Files.createDirectories(outputDir);
            String fileName = "transcript-%s-%s-en.txt".formatted(LocalDateTime.now().format(FILE_TIMESTAMP),
                    UUID.randomUUID().toString().substring(0, 8));
            return Files.writeString(outputDir.resolve(fileName), text, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to save transcript", e);
        }
    }
}
