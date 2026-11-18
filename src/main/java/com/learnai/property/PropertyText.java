package com.learnai.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds the plain-text description of a listing that is embedded in the vector store and given to the LLM. */
final class PropertyText {

    private PropertyText() {
    }

    static String describe(PropertyDetails d) {
        List<String> lines = new ArrayList<>();
        lines.add(d.title());
        lines.add("%s property for sale at %s %s.".formatted(d.propertyType(), d.price().toPlainString(), d.currency()));
        lines.add("Area: %s %s.".formatted(d.area().toPlainString(), d.areaUnit()));
        lines.add("Location: " + join(d.address().street(), d.address().locality(), d.address().city(),
                d.address().district(), d.address().state(), d.address().postalCode(), d.address().country()) + ".");
        lines.add("Coordinates: %s, %s.".formatted(d.coordinates().latitude(), d.coordinates().longitude()));
        UrbanDetails u = d.urban();
        if (u != null) {
            lines.add("Details: " + join(u.buildingType(), count(u.bedrooms(), "bedroom"), count(u.bathrooms(), "bathroom"),
                    u.floor() == null ? null : "floor " + u.floor() + (u.totalFloors() == null ? "" : " of " + u.totalFloors()),
                    u.furnishing(), flag(u.parking(), "parking")) + ".");
        }
        RuralDetails r = d.rural();
        if (r != null) {
            lines.add("Details: " + join(r.landUse() == null ? null : r.landUse() + " land",
                    r.soilType() == null ? null : r.soilType() + " soil",
                    r.waterSource() == null ? null : "water from " + r.waterSource(), flag(r.irrigated(), "irrigated"),
                    flag(r.roadAccess(), "road access"), flag(r.electricity(), "electricity")) + ".");
        }
        if (d.description() != null && !d.description().isBlank()) {
            lines.add(d.description().trim());
        }
        return String.join("\n", lines);
    }

    private static String count(Integer n, String noun) {
        return n == null ? null : n + " " + noun + (n == 1 ? "" : "s");
    }

    private static String flag(Boolean value, String label) {
        return value == null ? null : (value ? "" : "no ") + label;
    }

    private static String join(String... parts) {
        return Stream.of(parts).filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }
}
