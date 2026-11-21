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

    private static final String MARKET_PROMPT = """
            You are a real-estate market analyst working on Indian property listings.
            You are given statistics computed from the available listings that match a buyer's request.
            Write a short market summary (at most 90 words) covering the typical price, the price per square foot when
            given (sale) or the monthly rent and deposit (rent), the spread between the cheapest and the dearest
            listing, and the cities with the most options. Use only these statistics; never invent listings or prices.
            Amounts are Indian rupees (INR). Call out when there are too few listings for a reliable comparison.
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
                        // Masked text: the answer must not reveal the exact address, contact or survey number.
                        PropertyText.describe(m.property().details().masked())))
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

    /**
     * RAG market-research step: describes the aggregated statistics of the matching listings. Returns null when no
     * chat model is configured, there are no listings, or the model call fails.
     */
    public String summarizeMarket(String query, MarketResearch research) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null || research == null || research.listingCount() == 0) {
            return null;
        }
        try {
            String summary = ChatClient.create(model).prompt()
                    .system(MARKET_PROMPT)
                    .user("Buyer request: " + query + "\n\nMarket statistics:\n" + statistics(research))
                    .call()
                    .content();
            return summary != null ? summary.trim() : null;
        }
        catch (RuntimeException e) {
            log.warn("Failed to generate market research: {}", e.getMessage());
            return null;
        }
    }

    private static String statistics(MarketResearch research) {
        StringBuilder text = new StringBuilder();
        text.append("Listings: ").append(research.listingCount()).append('\n');
        text.append("Average price: ").append(research.averagePrice()).append(" INR\n");
        text.append("Median price: ").append(research.medianPrice()).append(" INR\n");
        text.append("Price range: ").append(research.minPrice()).append(" to ").append(research.maxPrice())
                .append(" INR\n");
        if (research.averagePricePerSqFt() != null) {
            text.append("Average price per square foot: ").append(research.averagePricePerSqFt()).append(" INR\n");
        }
        if (research.averageDeposit() != null) {
            text.append("Average security deposit: ").append(research.averageDeposit()).append(" INR\n");
        }
        if (!research.listingsByCity().isEmpty()) {
            text.append("Listings by city: ").append(research.listingsByCity()).append('\n');
        }
        return text.toString();
    }
}
