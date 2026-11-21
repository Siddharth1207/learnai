package com.learnai.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.util.MimeType;

import com.learnai.audio.AudioTranscriptionService;
import com.learnai.audio.InputLanguage;
import com.learnai.audio.TranscriptionResult;
import com.learnai.property.PropertySearchResult.PropertyMatch;
import com.learnai.vector.VectorSearchHit;
import com.learnai.vector.VectorSearchService;
import com.learnai.vector.VectorSearchService.TextEntry;

import jakarta.validation.Validation;

class PropertyServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
    private static final MimeType MP3 = MimeType.valueOf("audio/mpeg");
    private static final InputLanguage HINDI = InputLanguage.resolve("hi").orElseThrow();

    private final PropertyRepository properties = mock(PropertyRepository.class);
    private final PurchaseRepository purchases = mock(PurchaseRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final VectorSearchService vectorSearch = mock(VectorSearchService.class);
    private final AudioTranscriptionService transcription = mock(AudioTranscriptionService.class);
    private final PropertyExtractor extractor = mock(PropertyExtractor.class);
    private final PropertyAnswerGenerator answerGenerator = mock(PropertyAnswerGenerator.class);
    private final PropertyCatalog catalog = mock(PropertyCatalog.class);
    private final PropertySearchCache searchCache = mock(PropertySearchCache.class);
    private final Resource audio = new ByteArrayResource(new byte[] {1});

    private final PropertyService service = new PropertyService(properties, purchases, mongoTemplate, vectorSearch,
            transcription, extractor, answerGenerator, Validation.buildDefaultValidatorFactory().getValidator(), catalog,
            searchCache, Clock.fixed(NOW, ZoneOffset.UTC), 0.9, true);

    PropertyServiceTest() {
        when(properties.save(any(Property.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchases.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        when(searchCache.lookup(any())).thenReturn(Optional.empty());
        when(catalog.research(any())).thenReturn(emptyResearch());
    }

    @Test
    void formListingIsSavedAndIndexedWithTypeCategory() {
        when(vectorSearch.isEnabled()).thenReturn(true);
        when(vectorSearch.addTexts(anyList())).thenReturn(List.of("vec-1"));

        Property property = service.createFromForm(PropertyFixtures.urbanFlat());

        assertThat(property.status()).isEqualTo(PropertyStatus.AVAILABLE);
        assertThat(property.source()).isEqualTo(PropertySource.FORM);
        assertThat(property.vectorDocumentId()).isEqualTo("vec-1");
        assertThat(property.createdAt()).isEqualTo(NOW);
        assertThat(property.details().currency()).isEqualTo("INR");
        assertThat(property.details().title()).isEqualTo("Urban property in Pune");
        verify(searchCache).invalidate();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TextEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(vectorSearch).addTexts(entries.capture());
        TextEntry entry = entries.getValue().get(0);
        assertThat(entry.metadata()).containsEntry("category", "property-urban").containsEntry("propertyId", property.id());
        assertThat(entry.text()).contains("Koregaon Park", "18.5362, 73.894", "2 bedrooms", "9500000 INR");
    }

    @Test
    void listingIsSavedWithoutIndexWhenVectorStoreDisabled() {
        when(vectorSearch.isEnabled()).thenReturn(false);

        Property property = service.createFromForm(PropertyFixtures.ruralFarm());

        assertThat(property.vectorDocumentId()).isNull();
        assertThat(property.details().currency()).isEqualTo("INR");
        verify(vectorSearch, never()).addTexts(anyList());
    }

    @Test
    void listingIsSavedWhenIndexingFails() {
        when(vectorSearch.isEnabled()).thenReturn(true);
        when(vectorSearch.addTexts(anyList())).thenThrow(new IllegalStateException("quota"));

        assertThat(service.createFromForm(PropertyFixtures.urbanFlat()).vectorDocumentId()).isNull();
    }

    @Test
    void detailsForOtherTypeAreDropped() {
        PropertyDetails farmWithUrban = PropertyFixtures.ruralFarm()
                .withOverrides(new PropertyDetails(null, null, null, null, null, null, null, null, null, null, null,
                        new UrbanDetails("flat", 1, 1, 1, 1, null, null), null));

        assertThat(service.createFromForm(farmWithUrban).details().urban()).isNull();
    }

    @Test
    void voiceListingUsesExtractedDetailsWithDeviceCoordinates() {
        TranscriptionResult transcript = new TranscriptionResult("hi", "...", true, "Two bedroom flat in Pune", "t.txt");
        when(transcription.transcribe(audio, MP3, HINDI)).thenReturn(transcript);
        PropertyDetails extracted = PropertyFixtures.urbanFlat()
                .withOverrides(PropertyDetails.empty());
        extracted = new PropertyDetails(extracted.sellerName(), extracted.sellerContact(), extracted.propertyType(), null,
                extracted.description(), extracted.address(), null, extracted.area(), extracted.areaUnit(),
                extracted.price(), null, extracted.urban(), null);
        when(extractor.extract("Two bedroom flat in Pune")).thenReturn(extracted);
        Coordinates gps = new Coordinates(18.5, 73.8);

        VoicePropertyResult result = service.createFromVoice(audio, MP3, HINDI, overrides(gps));

        assertThat(result.transcription()).isEqualTo(transcript);
        assertThat(result.property().source()).isEqualTo(PropertySource.VOICE);
        assertThat(result.property().transcript()).isEqualTo("Two bedroom flat in Pune");
        assertThat(result.property().details().coordinates()).isEqualTo(gps);
    }

    @Test
    void incompleteVoiceListingReturnsDraftAndMissingFields() {
        TranscriptionResult transcript = new TranscriptionResult("en", "Farm for sale", false, "Farm for sale", "t.txt");
        when(transcription.transcribe(audio, MP3, InputLanguage.ENGLISH)).thenReturn(transcript);
        when(extractor.extract("Farm for sale")).thenReturn(new PropertyDetails(null, null, PropertyType.RURAL, null,
                "Farm for sale", null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> service.createFromVoice(audio, MP3, InputLanguage.ENGLISH,
                new PropertyDetails("Ramesh", "+91 1", null, null, null, null, null, null, null, null, null, null, null)))
                .isInstanceOfSatisfying(IncompletePropertyException.class, e -> {
                    assertThat(e.transcription()).isEqualTo(transcript);
                    assertThat(e.draft().sellerName()).isEqualTo("Ramesh");
                    assertThat(e.draft().propertyType()).isEqualTo(PropertyType.RURAL);
                    assertThat(e.errors()).extracting(IncompletePropertyException.FieldProblem::field)
                            .containsExactly("address", "area", "areaUnit", "coordinates", "price");
                });
        verify(properties, never()).save(any());
    }

    @Test
    void searchKeepsAvailableListingsThatPassFiltersInVectorOrder() {
        Property near = saved("near", PropertyFixtures.urbanFlat(), PropertyStatus.AVAILABLE);
        Property sold = saved("sold", PropertyFixtures.urbanFlat(), PropertyStatus.SOLD);
        Property far = saved("far", PropertyFixtures.ruralFarm(), PropertyStatus.AVAILABLE);
        when(vectorSearch.isEnabled()).thenReturn(true);
        when(vectorSearch.search(eq("flat in pune"), anyInt(), eq(0.9), eq(PropertyService.categoryFilter(null))))
                .thenReturn(List.of(hit("sold", 0.9), hit("near", 0.8), hit("far", 0.7), hit("deleted", 0.6)));
        when(properties.findAllById(any())).thenReturn(List.of(near, sold, far));
        when(answerGenerator.answer(eq("flat in pune"), anyList())).thenReturn("Listing near fits best.");

        PropertySearchResult result = service.search(new PropertySearch("flat in pune", null, null, null,
                new Coordinates(18.52, 73.86), 50.0, 5));

        assertThat(result.answer()).isEqualTo("Listing near fits best.");
        assertThat(result.results()).singleElement().satisfies(m -> {
            assertThat(m.property().id()).isEqualTo("near");
            assertThat(m.score()).isEqualTo(0.8);
            assertThat(m.distanceKm()).isBetween(1.0, 10.0);
        });
    }

    @Test
    void searchAppliesPriceAndTypeFilters() {
        Property flat = saved("flat", PropertyFixtures.urbanFlat(), PropertyStatus.AVAILABLE);
        Property farm = saved("farm", PropertyFixtures.ruralFarm(), PropertyStatus.AVAILABLE);
        when(vectorSearch.isEnabled()).thenReturn(true);
        when(vectorSearch.search(any(), anyInt(), eq(0.9), eq("category == 'property-rural'")))
                .thenReturn(List.of(hit("flat", 0.9), hit("farm", 0.8)));
        when(properties.findAllById(any())).thenReturn(List.of(flat, farm));

        PropertySearchResult result = service.search(new PropertySearch("land", PropertyType.RURAL, null,
                new BigDecimal("5000000"), null, null, null));

        assertThat(result.results()).extracting(m -> m.property().id()).containsExactly("farm");
        assertThat(result.results()).extracting(PropertyMatch::distanceKm).containsOnlyNulls();
    }

    @Test
    void voiceSearchUsesEnglishTranscriptAsQuery() {
        TranscriptionResult transcript = new TranscriptionResult("hi", "...", true, "farm near Pune", "t.txt");
        when(transcription.transcribe(audio, MP3, HINDI)).thenReturn(transcript);
        when(vectorSearch.isEnabled()).thenReturn(true);
        when(vectorSearch.search(eq("farm near Pune"), eq(15), eq(0.9), any())).thenReturn(List.of());

        PropertySearchResult result = service.searchByVoice(audio, MP3, HINDI,
                new PropertySearch(null, null, null, null, null, null, 3));

        assertThat(result.transcription()).isEqualTo(transcript);
        assertThat(result.query()).isEqualTo("farm near Pune");
        assertThat(result.results()).isEmpty();
    }

    @Test
    void purchaseMarksListingSoldAndRecordsBuyer() {
        Property sold = new Property("p1", PropertyFixtures.urbanFlat().normalized(), PropertyStatus.SOLD,
                PropertySource.FORM, null, "vec-1", NOW, NOW, "ignored");
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(Property.class))).thenReturn(sold);

        Purchase purchase = service.purchase("p1", new PurchaseRequest(" Neha Singh ", "neha@example.com"));

        assertThat(purchase.propertyId()).isEqualTo("p1");
        assertThat(purchase.buyerName()).isEqualTo("Neha Singh");
        assertThat(purchase.price()).isEqualByComparingTo("9500000");
        assertThat(purchase.coordinates()).isEqualTo(new Coordinates(18.5362, 73.8940));
        assertThat(purchase.purchasedAt()).isEqualTo(NOW);
        verify(vectorSearch).delete(List.of("vec-1"));
        verify(searchCache).invalidate();

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findAndModify(query.capture(), any(Update.class), any(FindAndModifyOptions.class),
                eq(Property.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("status", PropertyStatus.AVAILABLE);
    }

    @Test
    void purchaseOfSoldListingConflicts() {
        when(properties.findById("p1")).thenReturn(Optional.of(saved("p1", PropertyFixtures.urbanFlat(), PropertyStatus.SOLD)));

        assertThatThrownBy(() -> service.purchase("p1", new PurchaseRequest("Neha", "neha@example.com")))
                .isInstanceOf(PropertyNotAvailableException.class);
        verify(purchases, never()).save(any());
    }

    @Test
    void purchaseOfUnknownListingIsNotFound() {
        when(properties.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purchase("missing", new PurchaseRequest("Neha", "neha@example.com")))
                .isInstanceOf(PropertyNotFoundException.class);
    }

    @Test
    void searchFiltersRentListings() {
        Property sale = saved("sale", PropertyFixtures.urbanFlat(), PropertyStatus.AVAILABLE);
        Property rent = saved("rent", PropertyFixtures.urbanRent(), PropertyStatus.AVAILABLE);
        when(vectorSearch.isEnabled()).thenReturn(true);
        when(vectorSearch.search(eq("flat"), anyInt(), eq(0.9), eq(PropertyService.categoryFilter(null))))
                .thenReturn(List.of(hit("sale", 0.9), hit("rent", 0.8)));
        when(properties.findAllById(any())).thenReturn(List.of(sale, rent));

        PropertySearchResult result = service.search(new PropertySearch("flat", null, null, null, null, null, null,
                ListingType.RENT));

        assertThat(result.results()).extracting(m -> m.property().id()).containsExactly("rent");
    }

    @Test
    void searchUsesMongoCatalogWhenVectorStoreIsDisabled() {
        Property flat = saved("flat", PropertyFixtures.urbanFlat(), PropertyStatus.AVAILABLE);
        when(catalog.search(any(), anyInt())).thenReturn(List.of(new PropertyCatalog.Match(flat, 0.75)));
        when(answerGenerator.answer(eq("sunny flat"), anyList())).thenReturn("Here it is.");

        PropertySearchResult result = service.search(new PropertySearch("sunny flat", null, null, null, null, null, null));

        assertThat(result.answer()).isEqualTo("Here it is.");
        assertThat(result.results()).singleElement().satisfies(m -> {
            assertThat(m.property().id()).isEqualTo("flat");
            assertThat(m.score()).isEqualTo(0.75);
        });
        verify(vectorSearch, never()).search(any(), anyInt(), anyDouble(), any());
        verify(searchCache).store(any(), any());
    }

    @Test
    void searchAddsMarketResearchAndCachesTheResponse() {
        Property flat = saved("flat", PropertyFixtures.urbanFlat(), PropertyStatus.AVAILABLE);
        when(catalog.search(any(), anyInt())).thenReturn(List.of(new PropertyCatalog.Match(flat, 1.0)));
        MarketResearch stats = new MarketResearch(3, new BigDecimal("8000000"), new BigDecimal("8500000"),
                new BigDecimal("5000000"), new BigDecimal("12000000"), new BigDecimal("7000"), null,
                Map.of("Pune", 3L), null);
        when(catalog.research(any())).thenReturn(stats);
        when(answerGenerator.summarizeMarket(eq("flat in pune"), eq(stats))).thenReturn("Prices in Pune start at 50 lakh.");

        PropertySearchResult result = service.search(new PropertySearch("flat in pune", null, null, null, null, null, null));

        assertThat(result.marketResearch()).isNotNull();
        assertThat(result.marketResearch().listingCount()).isEqualTo(3);
        assertThat(result.marketResearch().aiSummary()).isEqualTo("Prices in Pune start at 50 lakh.");
        verify(searchCache).store(any(), any());
    }

    @Test
    void cachedSearchSkipsCatalogVectorAndLlm() {
        Property flat = saved("flat", PropertyFixtures.urbanFlat(), PropertyStatus.AVAILABLE);
        MarketResearch stats = new MarketResearch(1, new BigDecimal("9500000"), new BigDecimal("9500000"),
                new BigDecimal("9500000"), new BigDecimal("9500000"), null, null, Map.of("Pune", 1L), "Cached summary");
        when(searchCache.lookup(any())).thenReturn(Optional.of(new PropertySearchResult(null, "flat", "Cached answer",
                List.of(new PropertyMatch(flat, 0.9, null)), stats)));

        PropertySearchResult result = service.search(new PropertySearch("flat", null, null, null, null, null, null));

        assertThat(result.answer()).isEqualTo("Cached answer");
        assertThat(result.query()).isEqualTo("flat");
        assertThat(result.marketResearch()).isSameAs(stats);
        verify(catalog, never()).search(any(), anyInt());
        verify(vectorSearch, never()).search(any(), anyInt(), anyDouble(), any());
        verify(answerGenerator, never()).answer(any(), anyList());
        verify(searchCache, never()).store(any(), any());
    }

    @Test
    void listFiltersByListingType() {
        when(mongoTemplate.find(any(Query.class), eq(Property.class))).thenReturn(List.of());

        service.list(null, null, ListingType.RENT);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(query.capture(), eq(Property.class));
        assertThat(query.getValue().getQueryObject()).containsEntry("details.listingType", ListingType.RENT);
    }

    @Test
    void publicViewMasksContactAddressAndSurveyNumber() {
        Property masked = saved("farm", PropertyFixtures.ruralFarm(), PropertyStatus.AVAILABLE).publicView();

        assertThat(masked.details().sellerContact()).isEqualTo("+91 ••••• 00002");
        assertThat(masked.details().address().street()).isNull();
        assertThat(masked.details().address().postalCode()).isNull();
        assertThat(masked.details().address().locality()).isEqualTo("Wagholi village");
        assertThat(masked.details().rural().surveyNumber()).isNull();
        assertThat(masked.details().rural().crop()).isEqualTo("Mango");

        Property full = saved("flat", PropertyFixtures.urbanFlat(), PropertyStatus.AVAILABLE);
        assertThat(full.details().sellerContact()).isEqualTo("+91 98100 00001");
        assertThat(full.publicView().details().sellerContact()).isEqualTo("+91 ••••• 00001");
    }

    private static MarketResearch emptyResearch() {
        return new MarketResearch(0, null, null, null, null, null, null, Map.of(), null);
    }

    private static PropertyDetails overrides(Coordinates coordinates) {
        return new PropertyDetails(null, null, null, null, null, null, coordinates, null, null, null, null, null, null);
    }

    private static Property saved(String id, PropertyDetails details, PropertyStatus status) {
        return new Property(id, details.normalized(), status, PropertySource.FORM, null, "vec-" + id, NOW, null, null);
    }

    private static VectorSearchHit hit(String propertyId, double score) {
        return new VectorSearchHit("vec-" + propertyId, "text", score, Map.of("propertyId", propertyId));
    }
}
