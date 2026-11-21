package com.learnai.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class PropertyCatalogTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final PropertyCatalog catalog = new PropertyCatalog(mongo);

    @Test
    void queryTermsIgnoreStopWordsAndShortWords() {
        assertThat(PropertyCatalog.terms("Farm land with water near Pune under 50 lakh"))
                .containsExactly("farm", "land", "water", "pune", "lakh");
        assertThat(PropertyCatalog.terms("  ")).isEmpty();
    }

    @Test
    void relevanceCountsKeywordsFoundInTheListingText() {
        Property flat = property("flat", PropertyFixtures.urbanFlat());

        assertThat(PropertyCatalog.relevance("sunny flat pune", flat)).isEqualTo(1.0);
        assertThat(PropertyCatalog.relevance("sunny flat orchard", flat)).isEqualTo(0.67);
        assertThat(PropertyCatalog.relevance("", flat)).isZero();
    }

    @Test
    void searchAppliesHardFiltersAndRanksByRelevance() {
        Property flat = property("flat", PropertyFixtures.urbanFlat());
        when(mongo.find(any(Query.class), eq(Property.class))).thenReturn(List.of(flat));

        List<PropertyCatalog.Match> matches = catalog.search(new PropertySearch("sunny flat", PropertyType.URBAN,
                new BigDecimal("1000000"), new BigDecimal("15000000"), null, null, 5), 5);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.property().id()).isEqualTo("flat");
            assertThat(match.relevance()).isEqualTo(1.0);
        });
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Property.class));
        org.bson.Document criteria = query.getValue().getQueryObject();
        assertThat(criteria.keySet()).contains("status", "details.propertyType", "details.price", "$or");
        assertThat(criteria.get("status")).isEqualTo(PropertyStatus.AVAILABLE);
        assertThat(criteria.get("$or")).isNotNull();
    }

    @Test
    void searchFallsBackToFilterMatchesWhenNoKeywordMatches() {
        Property farm = property("farm", PropertyFixtures.ruralFarm());
        when(mongo.find(any(Query.class), eq(Property.class))).thenReturn(List.of(), List.of(farm));

        List<PropertyCatalog.Match> matches = catalog.search(new PropertySearch("penthouse", null, null, null, null,
                null, 5), 5);

        assertThat(matches).singleElement().satisfies(match -> {
            assertThat(match.property().id()).isEqualTo("farm");
            assertThat(match.relevance()).isZero();
        });
    }

    @Test
    void researchAggregatesPricesPerSquareFootAndCities() {
        List<Property> cohort = List.of(
                property("a", PropertyFixtures.urbanFlat()),
                property("b", PropertyFixtures.urbanRent()),
                property("c", PropertyFixtures.ruralFarm()));
        when(mongo.find(any(Query.class), eq(Property.class))).thenReturn(cohort);

        MarketResearch research = catalog.research(new PropertySearch("pune", null, null, null, null, null, 5));

        assertThat(research.listingCount()).isEqualTo(3);
        assertThat(research.averagePrice()).isEqualByComparingTo("4511667");
        assertThat(research.medianPrice()).isEqualByComparingTo("4000000");
        assertThat(research.minPrice()).isEqualByComparingTo("35000");
        assertThat(research.maxPrice()).isEqualByComparingTo("9500000");
        assertThat(research.averagePricePerSqFt()).isNotNull();
        assertThat(research.averageDeposit()).isNull();
        assertThat(research.listingsByCity()).containsEntry("Pune", 2L).containsEntry("Shirur", 1L);
    }

    @Test
    void researchReportsAverageDepositForRentSearches() {
        when(mongo.find(any(Query.class), eq(Property.class)))
                .thenReturn(List.of(property("rent", PropertyFixtures.urbanRent())));

        MarketResearch research = catalog.research(new PropertySearch("flat", null, null, null, null, null, 5, null));

        assertThat(research.averageDeposit()).isNull();
        MarketResearch rent = catalog.research(new PropertySearch("flat", null, null, null, null, null, 5,
                ListingType.RENT));
        assertThat(rent.averageDeposit()).isEqualByComparingTo("100000");
    }

    @Test
    void researchOfEmptyCohortHasNoStatistics() {
        when(mongo.find(any(Query.class), eq(Property.class))).thenReturn(List.of());

        MarketResearch research = catalog.research(new PropertySearch("flat", null, null, null, null, null, 5));

        assertThat(research.listingCount()).isZero();
        assertThat(research.averagePrice()).isNull();
        assertThat(research.listingsByCity()).isEmpty();
    }

    private static Property property(String id, PropertyDetails details) {
        return new Property(id, details.normalized(), PropertyStatus.AVAILABLE, PropertySource.FORM, null, null, NOW,
                null, null);
    }
}
