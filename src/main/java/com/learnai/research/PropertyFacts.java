package com.learnai.research;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.learnai.property.Address;
import com.learnai.property.ListingType;
import com.learnai.property.Property;
import com.learnai.property.PropertyDetails;
import com.learnai.property.UrbanDetails;

/** Compact, contact-free facts about a listing that are given to the research prompts. */
final class PropertyFacts {

    private PropertyFacts() {
    }

    /** For example {@code Koregaon Park, Pune, Maharashtra, India}; repeated parts (city = district) are merged. */
    static String location(PropertyDetails details) {
        Address address = details.address();
        String value = Stream.of(address.locality(), address.city(), address.district(), address.state(),
                        address.country() == null ? "India" : address.country())
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .distinct()
                .collect(Collectors.joining(", "));
        return value.isBlank() ? address.city() : value;
    }

    static String of(Property property) {
        PropertyDetails details = property.details();
        StringBuilder facts = new StringBuilder();
        facts.append(details.listingType() == ListingType.RENT ? "RENT" : "SALE").append(" listing");
        facts.append(", ").append(details.propertyType()).append(" property");
        facts.append(", price ").append(details.price().toPlainString()).append(' ')
                .append(details.currency() == null ? "INR" : details.currency());
        if (details.listingType() == ListingType.RENT && details.deposit() != null) {
            facts.append(", deposit ").append(details.deposit().toPlainString());
        }
        facts.append(", area ").append(details.area().toPlainString()).append(' ').append(details.areaUnit());
        facts.append(", at ").append(location(details));
        UrbanDetails urban = details.urban();
        if (urban != null) {
            facts.append(". ").append(join(urban.buildingType(),
                    urban.bedrooms() == null ? null : urban.bedrooms() + " bedroom",
                    urban.apartmentName(), urban.furnishing(),
                    urban.amenities() == null ? null : "amenities: " + urban.amenities()));
        }
        if (details.title() != null) {
            facts.append(". ").append(details.title().trim());
        }
        return facts.toString();
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }
}
