package com.learnai.property;

import jakarta.validation.constraints.NotBlank;

/**
 * @param locality neighbourhood, colony or village
 * @param city city or town (for rural land, the nearest town)
 */
public record Address(String street, String locality, @NotBlank String city, String district, String state,
        String postalCode, String country) {

    /** Public view: the exact street and postal code stay hidden until a purchase or a CONTACT_UNLOCK order. */
    public Address masked() {
        return new Address(null, locality, city, district, state, null, country);
    }
}
