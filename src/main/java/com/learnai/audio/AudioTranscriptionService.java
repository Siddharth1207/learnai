package com.learnai.audio;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Converts speech to text and, for non-English audio, translates the transcript to English.
 * The English text is written to a new file in {@code learnai.audio.output-dir}.
 */
@Service
public class AudioTranscriptionService {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ObjectProvider<TranscriptionModel> transcriptionModel;
    private final TextTranslator translator;
    private final Path outputDir;

    public AudioTranscriptionService(ObjectProvider<TranscriptionModel> transcriptionModel,
            TextTranslator translator, @Value("${learnai.audio.output-dir:transcripts}") Path outputDir) {
        this.transcriptionModel = transcriptionModel;
        this.translator = translator;
        this.outputDir = outputDir;
    }

    public TranscriptionResult transcribe(Resource audio, InputLanguage language) {
        TranscriptionModel model = transcriptionModel.getIfAvailable();
        if (model == null) {
            throw new AiNotConfiguredException(
                    "Speech-to-text is not configured. Set LEARNAI_CHAT_MODEL=openai and OPENAI_API_KEY.");
        }
        String transcript = model.transcribe(audio, optionsFor(model, language)).trim();

        boolean translate = !language.isEnglish() && !transcript.isEmpty();
        String englishText = translate ? translator.translateToEnglish(transcript, language) : transcript;

        Path savedFile = save(englishText);
        return new TranscriptionResult(language.code(), transcript, translate, englishText, savedFile.toString());
    }

    // Spring AI's merge overwrites the configured model, so start from the model's own defaults.
    private static OpenAiAudioTranscriptionOptions optionsFor(TranscriptionModel model, InputLanguage language) {
        OpenAiAudioTranscriptionOptions.Builder options = OpenAiAudioTranscriptionOptions.builder();
        if (model instanceof OpenAiAudioTranscriptionModel openAiModel) {
            options.from(openAiModel.getOptions());
        }
        return options.language(language.code()).build();
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
