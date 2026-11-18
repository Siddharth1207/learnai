package com.learnai.property;

import jakarta.validation.constraints.NotBlank;

/**
 * @param locality neighbourhood, colony or village
 * @param city city or town (for rural land, the nearest town)
 */
public record Address(String street, String locality, @NotBlank String city, String district, String state,
        String postalCode, String country) {
}
