package com.learnai.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

import com.learnai.property.Address;
import com.learnai.property.AreaUnit;
import com.learnai.property.Coordinates;
import com.learnai.property.KpiImpact;
import com.learnai.property.ListingType;
import com.learnai.property.LocationInsights;
import com.learnai.property.LocationKpi;
import com.learnai.property.LocationRecommendation;
import com.learnai.property.Property;
import com.learnai.property.PropertyDetails;
import com.learnai.property.PropertySource;
import com.learnai.property.PropertyStatus;
import com.learnai.property.PropertyType;
import com.learnai.property.UrbanDetails;

class LocationResearchServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private final WebSearchClient web = mock(WebSearchClient.class);
    private final ResearchSummarizer summarizer = new ResearchSummarizer(noChatModel());
    private final LocationResearchService service = new LocationResearchService(web, summarizer,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void researchRunsEveryKpiAndRecommendsBuyForApprovedProjects() {
        when(web.isEnabled()).thenReturn(true);
        when(web.search(anyString(), anyList())).thenReturn(List.of(new WebSource("Metro line 3 approved",
                "https://pune.gov.in/metro-3", "Pune Metro line 3 was sanctioned and a new station is coming up "
                        + "near Koregaon Park.", "2026-11-01")));

        LocationInsights insights = service.research(listing(ListingType.SALE));

        assertThat(insights.location()).isEqualTo("Koregaon Park, Pune, Maharashtra, India");
        assertThat(insights.generatedAt()).isEqualTo(NOW);
        assertThat(insights.kpis()).hasSize(LocationResearchService.TOPICS.size())
                .allSatisfy(kpi -> {
                    assertThat(kpi.impact()).isEqualTo(KpiImpact.POSITIVE);
                    assertThat(kpi.sources()).singleElement()
                            .satisfies(source -> assertThat(source.url()).isEqualTo("https://pune.gov.in/metro-3"));
                });
        assertThat(insights.suitabilityScore()).isEqualTo(95);
        assertThat(insights.recommendation()).isEqualTo(LocationRecommendation.BUY);
        assertThat(insights.summary()).contains("Suitability score 95/100", "verdict BUY", "Transport");
        assertThat(insights.sources()).hasSize(1);
    }

    @Test
    void landAcquisitionSignalHoldsTheRecommendation() {
        when(web.isEnabled()).thenReturn(true);
        when(web.search(anyString(), anyList())).thenReturn(List.of(new WebSource("Road widening notice",
                "https://pmc.gov.in/notice", "Land acquisition notice for road widening near the village.", null)));

        LocationInsights insights = service.research(listing(ListingType.SALE));

        assertThat(insights.kpis()).allSatisfy(kpi -> assertThat(kpi.impact()).isEqualTo(KpiImpact.NEGATIVE));
        assertThat(insights.recommendation()).isEqualTo(LocationRecommendation.HOLD);
        assertThat(insights.suitabilityScore()).isEqualTo(5);
        assertThat(insights.summary()).contains("Risks to check");
    }

    @Test
    void researchWithoutASearchKeyIsUnavailable() {
        when(web.isEnabled()).thenReturn(false);

        assertThatThrownBy(() -> service.research(listing(ListingType.SALE)))
                .isInstanceOf(ResearchNotConfiguredException.class);
        verify(web, never()).search(anyString(), anyList());
    }

    @Test
    void rentalListingRentsAtALowerScore() {
        List<LocationKpi> kpis = List.of(
                new LocationKpi("transport", "Transport and connectivity", KpiImpact.POSITIVE, "Metro.", List.of()),
                new LocationKpi("roads", "Roads and traffic", KpiImpact.NEUTRAL, "No change.", List.of()));

        assertThat(LocationResearchService.recommendation(kpis, 60, ListingType.RENT))
                .isEqualTo(LocationRecommendation.RENT);
        assertThat(LocationResearchService.recommendation(kpis, 60, ListingType.SALE))
                .isEqualTo(LocationRecommendation.HOLD);
        List<LocationKpi> risk = List.of(
                new LocationKpi("governance", "Planning and permissions", KpiImpact.NEGATIVE, "Stay order.", List.of()));
        assertThat(LocationResearchService.recommendation(risk, 90, ListingType.SALE))
                .isEqualTo(LocationRecommendation.HOLD);
    }

    @Test
    void sourcesDropListingPortalsAndDuplicates() {
        List<WebSource> sources = List.of(
                new WebSource("Portal", "https://www.magicbricks.com/flats", "Buy now", null),
                new WebSource("Notice", "https://pmc.gov.in/notice", "Widening", null),
                new WebSource("Notice again", "https://pmc.gov.in/notice", "Widening", null),
                new WebSource("News", "https://timesofindia.indiatimes.com/metro", "Metro line 3", null));

        assertThat(LocationResearchService.relevant(sources)).extracting(WebSource::title)
                .containsExactly("Notice", "News");
    }

    @Test
    void heuristicTreatsRisksAsNegativeButIgnoresUnconfirmedSnippets() {
        assertThat(LocationResearchService.heuristicImpact(List.of(
                new WebSource("Metro approved", "https://x.gov.in", "New metro station approved", null))))
                .isEqualTo(KpiImpact.POSITIVE);
        assertThat(LocationResearchService.heuristicImpact(List.of(
                new WebSource("Metro approved", "https://x.gov.in",
                        "New metro station approved but a stay order is in place", null))))
                .isEqualTo(KpiImpact.NEGATIVE);
        assertThat(LocationResearchService.heuristicImpact(List.of(
                new WebSource("Weather", "https://x.gov.in", "It rained in the city today", null))))
                .isEqualTo(KpiImpact.UNCERTAIN);
        assertThat(LocationResearchService.heuristicImpact(List.of())).isEqualTo(KpiImpact.UNCERTAIN);
    }

    @Test
    void summarizerParsesTheTwoLineAnswer() {
        KpiAssessment assessment = ResearchSummarizer.parse("IMPACT: Negative\nSUMMARY: A road widening plan "
                + "affects the plot.\nExtra detail.");

        assertThat(assessment.impact()).isEqualTo(KpiImpact.NEGATIVE);
        assertThat(assessment.summary()).isEqualTo("A road widening plan affects the plot. Extra detail.");
        assertThat(ResearchSummarizer.parse("no structure").impact()).isEqualTo(KpiImpact.UNCERTAIN);
    }

    private static Property listing(ListingType listingType) {
        PropertyDetails details = new PropertyDetails("Asha Verma", "+91 98100 00001", PropertyType.URBAN,
                "Sunny flat near the metro station", "2BHK with a balcony",
                new Address("12 MG Road", "Koregaon Park", "Pune", "Pune", "Maharashtra", "411001", "India"),
                new Coordinates(18.5362, 73.8940), new BigDecimal("1200"), AreaUnit.SQ_FT, new BigDecimal("9500000"),
                "INR", listingType, null, new UrbanDetails("apartment", "Lotus Residency", "Tower B", 2, 2, 5, 12,
                        "semi-furnished", true, "Lift, gym"),
                null);
        return new Property("p1", details, PropertyStatus.AVAILABLE, PropertySource.FORM, null, null, NOW, null,
                null);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ChatModel> noChatModel() {
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
