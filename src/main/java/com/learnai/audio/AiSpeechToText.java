package com.learnai.audio;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

/**
 * Transcribes audio by sending it inline to a multimodal chat model (Gemini).
 */
@Component
public class AiSpeechToText implements SpeechToText {

    private static final String PROMPT = """
            Transcribe the speech in this audio verbatim. The spoken language is {language}.
            Write the transcript in {language}, in its native script. Do not translate, summarise or add notes.
            Reply with the transcript only. If there is no speech, reply with an empty message.
            """;

    private final ObjectProvider<ChatModel> chatModel;

    public AiSpeechToText(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String transcribe(Resource audio, MimeType mimeType, InputLanguage language) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new AiNotConfiguredException(
                    "Speech-to-text is not configured. Set LEARNAI_CHAT_MODEL=google-genai and GEMINI_API_KEY.");
        }
        Media media = Media.builder().mimeType(mimeType).data(bytes(audio)).build();
        String transcript = ChatClient.create(model).prompt()
                .user(u -> u.text(PROMPT).param("language", language.displayName()).media(media))
                .call()
                .content();
        return transcript != null ? transcript.trim() : "";
    }

    private static byte[] bytes(Resource audio) {
        try {
            return audio.getContentAsByteArray();
        }
        catch (IOException e) {
            throw new UncheckedIOException("Failed to read audio", e);
        }
    }
}
