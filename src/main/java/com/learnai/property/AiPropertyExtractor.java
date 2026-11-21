package com.learnai.property;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.learnai.audio.AiNotConfiguredException;

@Component
public class AiPropertyExtractor implements PropertyExtractor {

    private static final String SYSTEM_PROMPT = """
            You extract real-estate listing details from a property seller's spoken description.
            Only use information stated in the description; set every field that was not mentioned to null. Never guess.
            - propertyType: URBAN for flats, apartments, houses, villas, shops, offices or plots in a city or town;
              RURAL for agricultural land, farms, farmhouses, orchards or plots in a village.
            - listingType: RENT when the seller wants to rent the property out, SALE when they want to sell it.
            - price: the full amount as a plain number (e.g. "50 lakh" = 5000000, "2 crore" = 20000000, "1.2 million" = 1200000).
            - deposit: the security deposit as a plain number, only for RENT listings.
            - currency: ISO 4217 code (e.g. INR for rupees, USD for dollars).
            - area and areaUnit: the size and its unit (SQ_FT, SQ_M, SQ_YD, ACRE or HECTARE). Convert bigha, guntha or
              kanal to ACRE only if the conversion is stated; otherwise leave area null.
            - coordinates: only if latitude and longitude are spoken as numbers.
            - address.city: the city or town; for village land use the nearest town or tehsil if mentioned.
            - urban: only for URBAN properties (building type, apartment or society name, tower/flat number, bedrooms,
              bathrooms, floor, total floors, furnishing, parking, amenities such as lift, gym or power backup).
            - rural: only for RURAL properties (land use, survey/khasra number, ownership type such as freehold or
              leasehold, crop, soil type, water source, irrigated, road access, electricity).
            - description: a short summary of everything the seller said about the property.
            """;

    private final ObjectProvider<ChatModel> chatModel;

    public AiPropertyExtractor(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public PropertyDetails extract(String description) {
        if (description == null || description.isBlank()) {
            return PropertyDetails.empty();
        }
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            throw new AiNotConfiguredException(
                    "Property extraction is not configured. Set LEARNAI_CHAT_MODEL=google-genai and GEMINI_API_KEY.");
        }
        PropertyDetails details = ChatClient.create(model).prompt()
                .system(SYSTEM_PROMPT)
                .user(description)
                .call()
                .entity(PropertyDetails.class);
        return details != null ? details : PropertyDetails.empty();
    }
}
