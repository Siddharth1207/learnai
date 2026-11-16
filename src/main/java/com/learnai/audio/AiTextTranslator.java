package com.learnai.audio;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class AiTextTranslator implements TextTranslator {

    private static final String SYSTEM_PROMPT = """
            You are a professional translator. Translate the user's text from {language} into English.
            Preserve meaning, tone and names. Reply with the English translation only, without notes or quotes.
            """;

    private final ObjectProvider<ChatModel> chatModel;

    public AiTextTranslator(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String translateToEnglish(String text, InputLanguage sourceLanguage) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new AiNotConfiguredException(
                    "Translation is not configured. Set LEARNAI_CHAT_MODEL=openai and OPENAI_API_KEY.");
        }
        String translation = ChatClient.create(model).prompt()
                .system(s -> s.text(SYSTEM_PROMPT).param("language", sourceLanguage.displayName()))
                .user(text)
                .call()
                .content();
        return translation != null ? translation.trim() : "";
    }
}
