package com.learnai.property;

import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.learnai.property.PropertySearchResult.PropertyMatch;

/** RAG step: answers the buyer's request using only the listings retrieved from the vector store. */
@Component
public class PropertyAnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(PropertyAnswerGenerator.class);

    private static final String SYSTEM_PROMPT = """
            You are a real-estate assistant helping a buyer choose a property.
            Answer the buyer's request using only the listings provided; do not invent properties or details.
            Recommend the best matches first, refer to each listing by its id, and explain briefly why it fits.
            If none of the listings fit the request, say so. Keep the answer under 150 words.
            """;

    private final ObjectProvider<ChatModel> chatModel;

    public PropertyAnswerGenerator(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    /** Returns null when no chat model is configured, there are no matches, or the model call fails. */
    public String answer(String query, List<PropertyMatch> matches) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null || matches.isEmpty()) {
            return null;
        }
        String listings = matches.stream()
                .map(m -> "[id: %s]%s\n%s".formatted(m.property().id(),
                        m.distanceKm() == null ? "" : " (%.1f km away)".formatted(m.distanceKm()),
                        PropertyText.describe(m.property().details())))
                .collect(Collectors.joining("\n\n"));
        try {
            String answer = ChatClient.create(model).prompt()
                    .system(SYSTEM_PROMPT)
                    .user("Buyer request: " + query + "\n\nListings:\n" + listings)
                    .call()
                    .content();
            return answer != null ? answer.trim() : null;
        }
        catch (RuntimeException e) {
            log.warn("Failed to generate property answer: {}", e.getMessage());
            return null;
        }
    }
}
