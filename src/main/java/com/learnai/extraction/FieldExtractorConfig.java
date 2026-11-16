package com.learnai.extraction;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class

FieldExtractorConfig {

    /**
     * Uses the AI extractor when a chat model is configured (e.g. OPENAI_API_KEY is set),
     * otherwise falls back to regex-based extraction.
     */
    @Bean
    FieldExtractor fieldExtractor(ObjectProvider<ChatModel> chatModel) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return new RegexFieldExtractor();
        }
        return new AiFieldExtractor(ChatClient.create(model));
    }
}
