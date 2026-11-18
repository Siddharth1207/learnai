package com.learnai.property;

import java.math.BigDecimal;

final class PropertyFixtures {

    private PropertyFixtures() {
    }

    static PropertyDetails urbanFlat() {
        return new PropertyDetails("Asha Verma", "+91 98100 00001", PropertyType.URBAN, null,
                "Sunny flat near the metro station with a balcony",
                new Address("12 MG Road", "Koregaon Park", "Pune", "Pune", "Maharashtra", "411001", "India"),
                new Coordinates(18.5362, 73.8940), new BigDecimal("1200"), AreaUnit.SQ_FT, new BigDecimal("9500000"),
                null, new UrbanDetails("apartment", 2, 2, 5, 12, "semi-furnished", true), null);
    }

    static PropertyDetails ruralFarm() {
        return new PropertyDetails("Ramesh Patil", "+91 98100 00002", PropertyType.RURAL, "Mango orchard with borewell",
                "Fertile farm land with mango trees and a farmhouse",
                new Address(null, "Wagholi village", "Shirur", "Pune", "Maharashtra", null, "India"),
                new Coordinates(18.8268, 74.3741), new BigDecimal("5"), AreaUnit.ACRE, new BigDecimal("4000000"), "inr",
                null, new RuralDetails("orchard", "black", "borewell", true, true, true));
    }
}
