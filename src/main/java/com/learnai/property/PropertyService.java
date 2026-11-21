package com.learnai.property;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import com.learnai.audio.AudioTranscriptionService;
import com.learnai.audio.InputLanguage;
import com.learnai.audio.TranscriptionResult;
import com.learnai.property.IncompletePropertyException.FieldProblem;
import com.learnai.property.PropertySearchResult.PropertyMatch;
import com.learnai.vector.VectorSearchHit;
import com.learnai.vector.VectorSearchService;
import com.learnai.vector.VectorSearchService.TextEntry;

import jakarta.validation.Validator;

/**
 * Seller listings (form or voice), buyer search and purchases.
 * Listings are always stored in MongoDB first. The vector store and the LLM are optional enhancements: listings are
 * also embedded there ({@code category=property-urban} or {@code property-rural}) when the vector store is enabled,
 * so a buyer's wording can be matched semantically on top of the MongoDB filters and keyword search.
 */
@Service
public class PropertyService {

    static final String PROPERTY_ID_METADATA = "propertyId";
    static final String CATEGORY_METADATA = "category";

    private static final Logger log = LoggerFactory.getLogger(PropertyService.class);
    /** Vector search fetches extra candidates because sold listings and hard filters are applied afterwards. */
    private static final int CANDIDATE_FACTOR = 5;
    private static final int MAX_CANDIDATES = 100;

    private final PropertyRepository properties;
    private final PurchaseRepository purchases;
    private final MongoTemplate mongoTemplate;
    private final VectorSearchService vectorSearch;
    private final AudioTranscriptionService transcription;
    private final PropertyExtractor extractor;
    private final PropertyAnswerGenerator answerGenerator;
    private final PropertyCatalog catalog;
    private final PropertySearchCache searchCache;
    private final Validator validator;
    private final Clock clock;
    private final double vectorSearchThreshold;
    private final boolean indexOnCreate;

    @Autowired
    public PropertyService(PropertyRepository properties, PurchaseRepository purchases, MongoTemplate mongoTemplate,
            VectorSearchService vectorSearch, AudioTranscriptionService transcription, PropertyExtractor extractor,
            PropertyAnswerGenerator answerGenerator, Validator validator, PropertyCatalog catalog,
            PropertySearchCache searchCache,
            @Value("${learnai.vector.search-threshold:0.9}") double vectorSearchThreshold,
            @Value("${learnai.vector.index-on-create:true}") boolean indexOnCreate) {
        this(properties, purchases, mongoTemplate, vectorSearch, transcription, extractor, answerGenerator, validator,
                catalog, searchCache, Clock.systemUTC(), vectorSearchThreshold, indexOnCreate);
    }

    PropertyService(PropertyRepository properties, PurchaseRepository purchases, MongoTemplate mongoTemplate,
            VectorSearchService vectorSearch, AudioTranscriptionService transcription, PropertyExtractor extractor,
            PropertyAnswerGenerator answerGenerator, Validator validator, PropertyCatalog catalog,
            PropertySearchCache searchCache, Clock clock, double vectorSearchThreshold, boolean indexOnCreate) {
        this.properties = properties;
        this.purchases = purchases;
        this.mongoTemplate = mongoTemplate;
        this.vectorSearch = vectorSearch;
        this.transcription = transcription;
        this.extractor = extractor;
        this.answerGenerator = answerGenerator;
        this.catalog = catalog;
        this.searchCache = searchCache;
        this.validator = validator;
        this.clock = clock;
        this.vectorSearchThreshold = vectorSearchThreshold;
        this.indexOnCreate = indexOnCreate;
    }

    /** Creates a listing from the form. {@code details} must already be valid. */
    public Property createFromForm(PropertyDetails details) {
        return create(details, PropertySource.FORM, null);
    }

    /**
     * Transcribes the seller's description (translating to English if needed), extracts the property details with
     * the LLM and creates the listing. Non-null fields of {@code overrides} (e.g. GPS coordinates from the device)
     * replace the extracted values.
     *
     * @throws IncompletePropertyException if required details are missing after extraction
     */
    public VoicePropertyResult createFromVoice(Resource audio, MimeType mimeType, InputLanguage language,
            PropertyDetails overrides) {
        TranscriptionResult transcript = transcription.transcribe(audio, mimeType, language);
        PropertyDetails draft = extractor.extract(transcript.englishText()).withOverrides(overrides);
        List<FieldProblem> problems = validator.validate(draft).stream()
                .map(v -> new FieldProblem(v.getPropertyPath().toString(), v.getMessage()))
                .sorted(Comparator.comparing(FieldProblem::field))
                .toList();
        if (!problems.isEmpty()) {
            throw new IncompletePropertyException(transcript, draft, problems);
        }
        return new VoicePropertyResult(transcript, create(draft, PropertySource.VOICE, transcript.englishText()));
    }

    public Property get(String id) {
        return properties.findById(id).orElseThrow(() -> new PropertyNotFoundException("Property " + id + " not found"));
    }

    public List<Property> list(PropertyType type, PropertyStatus status) {
        return list(type, status, null);
    }

    public List<Property> list(PropertyType type, PropertyStatus status, ListingType listingType) {
        Query query = new Query().with(Sort.by(Sort.Direction.DESC, Property.CREATED_AT));
        if (type != null) {
            query.addCriteria(where(Property.PROPERTY_TYPE).is(type));
        }
        if (status != null) {
            query.addCriteria(where(Property.STATUS).is(status));
        }
        if (listingType != null) {
            // Listings created before rent existed have no listingType field and count as SALE.
            query.addCriteria(listingType == ListingType.SALE
                    ? new Criteria().orOperator(where(Property.LISTING_TYPE).is(ListingType.SALE),
                            where(Property.LISTING_TYPE).is(null))
                    : where(Property.LISTING_TYPE).is(listingType));
        }
        return mongoTemplate.find(query, Property.class);
    }

    /**
     * Retrieves listings similar to the buyer's request from the vector store, keeps available listings that pass
     * the filters, and asks the LLM to answer the request from them (RAG).
     */
    public PropertySearchResult search(PropertySearch search) {
        return search(search, null);
    }

    /** Transcribes the buyer's spoken request and runs {@link #search(PropertySearch)} with it. */
    public PropertySearchResult searchByVoice(Resource audio, MimeType mimeType, InputLanguage language,
            PropertySearch filters) {
        TranscriptionResult transcript = transcription.transcribe(audio, mimeType, language);
        if (transcript.englishText().isBlank()) {
            return new PropertySearchResult(transcript, "", null, List.of());
        }
        return search(filters.withQuery(transcript.englishText()), transcript);
    }

    /**
     * DB-first search: cached responses are returned without touching the vector store or the LLM; otherwise the
     * listings are read from MongoDB (hard filters + keyword relevance), optionally merged with vector matches, and
     * the answer and market research are generated only for this new request. The response is then cached.
     */
    private PropertySearchResult search(PropertySearch search, TranscriptionResult transcript) {
        Optional<PropertySearchResult> cached = searchCache.lookup(search);
        if (cached.isPresent()) {
            PropertySearchResult hit = cached.get();
            return new PropertySearchResult(transcript, search.query(), hit.answer(), hit.results(),
                    hit.marketResearch());
        }
        List<PropertyMatch> matches = matches(search);
        String answer = answerGenerator.answer(search.query(), matches);
        PropertySearchResult result = new PropertySearchResult(transcript, search.query(), answer, matches,
                marketResearch(search));
        searchCache.store(search, result);
        return result;
    }

    /**
     * Matching listings, MongoDB first: relevance of the buyer's words in the stored listing text, then any extra
     * listings found with vector similarity (at least {@code learnai.vector.search-threshold}). Sold listings and
     * listings that fail the filters are dropped.
     */
    private List<PropertyMatch> matches(PropertySearch search) {
        Map<String, Double> vectorScores = vectorScores(search);
        List<PropertyMatch> matches = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (PropertyCatalog.Match candidate : catalog.search(search, search.limit() + vectorScores.size())) {
            Property property = candidate.property();
            Double vectorScore = vectorScores.remove(property.id());
            double score = vectorScore == null ? candidate.relevance()
                    : Math.max(vectorScore, candidate.relevance());
            matches.add(toMatch(property, score, search));
            seen.add(property.id());
        }
        if (!vectorScores.isEmpty()) {
            Map<String, Property> found = properties.findAllById(vectorScores.keySet()).stream()
                    .collect(Collectors.toMap(Property::id, Function.identity()));
            vectorScores.entrySet().stream()
                    .filter(e -> !seen.contains(e.getKey()))
                    .map(e -> found.get(e.getKey()) == null ? null
                            : toMatch(found.get(e.getKey()), e.getValue(), search))
                    .filter(Objects::nonNull)
                    .filter(m -> matches(m, search))
                    .sorted(Comparator.comparing(PropertyMatch::score).reversed())
                    .forEach(matches::add);
        }
        return matches.stream().limit(search.limit()).toList();
    }

    /** Vector matches with at least the configured similarity; empty when the vector store is disabled or fails. */
    private Map<String, Double> vectorScores(PropertySearch search) {
        Map<String, Double> scores = new LinkedHashMap<>();
        if (!vectorSearch.isEnabled()) {
            return scores;
        }
        int candidates = Math.min(MAX_CANDIDATES, search.limit() * CANDIDATE_FACTOR);
        try {
            for (VectorSearchHit hit : vectorSearch.search(search.query(), candidates, vectorSearchThreshold,
                    categoryFilter(search.propertyType()))) {
                Object propertyId = hit.metadata() == null ? null : hit.metadata().get(PROPERTY_ID_METADATA);
                if (propertyId != null) {
                    scores.putIfAbsent(propertyId.toString(), hit.score());
                }
            }
        }
        catch (RuntimeException e) {
            log.warn("Vector search failed, using MongoDB matches only: {}", e.getMessage());
        }
        return scores;
    }

    /** Price statistics from MongoDB plus Gemini's reading of them (the summary is null without a chat model). */
    private MarketResearch marketResearch(PropertySearch search) {
        MarketResearch research = catalog.research(search);
        String summary = answerGenerator.summarizeMarket(search.query(), research);
        return summary == null ? research : research.withAiSummary(summary);
    }

    /**
     * Buys an available listing at its listed price. The status change is atomic, so only one buyer can succeed.
     *
     * @throws PropertyNotFoundException if the listing does not exist
     * @throws PropertyNotAvailableException if it has already been sold
     */
    public Purchase purchase(String propertyId, PurchaseRequest request) {
        String purchaseId = UUID.randomUUID().toString();
        Instant now = clock.instant();
        Property sold = mongoTemplate.findAndModify(
                query(where("_id").is(propertyId).and(Property.STATUS).is(PropertyStatus.AVAILABLE)),
                new Update().set(Property.STATUS, PropertyStatus.SOLD).set(Property.SOLD_AT, now)
                        .set(Property.PURCHASE_ID, purchaseId),
                FindAndModifyOptions.options().returnNew(true), Property.class);
        if (sold == null) {
            get(propertyId);
            throw new PropertyNotAvailableException(propertyId);
        }
        PropertyDetails d = sold.details();
        Purchase purchase = purchases.save(new Purchase(purchaseId, sold.id(), d.propertyType(), d.title(), d.address(),
                d.coordinates(), d.price(), d.currency(), d.sellerName(), d.sellerContact(), request.buyerName().trim(),
                request.buyerContact().trim(), now));
        removeFromVectorStore(sold);
        searchCache.invalidate();
        return purchase;
    }

    public Purchase getPurchase(String propertyId) {
        get(propertyId);
        return purchases.findByPropertyId(propertyId)
                .orElseThrow(() -> new PropertyNotFoundException("Property " + propertyId + " has not been purchased"));
    }

    private Property create(PropertyDetails details, PropertySource source, String transcript) {
        PropertyDetails normalized = details.normalized();
        Property property = new Property(UUID.randomUUID().toString(), normalized, PropertyStatus.AVAILABLE, source,
                transcript, null, clock.instant(), null, null);
        // MongoDB is the source of truth: save first, then index best-effort so a failing/hanging LLM never loses a listing.
        Property saved = properties.save(property);
        searchCache.invalidate();
        if (!indexOnCreate) {
            log.info("Vector indexing on create is disabled; property {} is searchable from MongoDB only", saved.id());
            return saved;
        }
        String vectorDocumentId = index(saved);
        if (vectorDocumentId == null) {
            return saved;
        }
        try {
            mongoTemplate.updateFirst(query(where("_id").is(saved.id())),
                    new Update().set(Property.VECTOR_DOCUMENT_ID, vectorDocumentId), Property.class);
        }
        catch (RuntimeException e) {
            log.warn("Property {} is saved but its vector id could not be stored: {}", saved.id(), e.getMessage());
            return saved;
        }
        return saved.withVectorDocumentId(vectorDocumentId);
    }

    /** Embeds the listing in the vector store; returns null (listing is saved but not searchable) if that fails. */
    private String index(Property property) {
        if (!vectorSearch.isEnabled()) {
            log.info("Vector store disabled; property {} will not be searchable", property.id());
            return null;
        }
        try {
            Map<String, Object> metadata = Map.of(CATEGORY_METADATA, category(property.details().propertyType()),
                    PROPERTY_ID_METADATA, property.id());
            // Index the masked text so the RAG answer cannot reveal the exact address or survey number.
            return vectorSearch.addTexts(List.of(new TextEntry(PropertyText.describe(property.details().masked()), metadata)))
                    .get(0);
        }
        catch (RuntimeException e) {
            log.warn("Failed to index property {} in the vector store: {}", property.id(), e.getMessage());
            return null;
        }
    }

    private void removeFromVectorStore(Property sold) {
        if (sold.vectorDocumentId() != null && deleteVectorQuietly(sold.vectorDocumentId())) {
            mongoTemplate.updateFirst(query(where("_id").is(sold.id())), new Update().unset(Property.VECTOR_DOCUMENT_ID),
                    Property.class);
        }
    }

    private boolean deleteVectorQuietly(String vectorDocumentId) {
        try {
            vectorSearch.delete(List.of(vectorDocumentId));
            return true;
        }
        catch (RuntimeException e) {
            log.warn("Failed to delete vector document {}: {}", vectorDocumentId, e.getMessage());
            return false;
        }
    }

    private static PropertyMatch toMatch(Property property, Double score, PropertySearch search) {
        Double distance = search.near() == null ? null : search.near().distanceKm(property.details().coordinates());
        return new PropertyMatch(property, score, distance);
    }

    private static boolean matches(PropertyMatch match, PropertySearch search) {
        PropertyDetails d = match.property().details();
        return match.property().status() == PropertyStatus.AVAILABLE
                && (search.propertyType() == null || d.propertyType() == search.propertyType())
                && (search.listingType() == null || listingType(d) == search.listingType())
                && (search.minPrice() == null || d.price().compareTo(search.minPrice()) >= 0)
                && (search.maxPrice() == null || d.price().compareTo(search.maxPrice()) <= 0)
                && (search.radiusKm() == null || match.distanceKm() <= search.radiusKm());
    }

    /** Listings created before rent existed have no listing type and are for sale. */
    static ListingType listingType(PropertyDetails details) {
        return details.listingType() == null ? ListingType.SALE : details.listingType();
    }

    /** Uses the {@code category} metadata field, which is filterable in the default vector index. */
    static String categoryFilter(PropertyType type) {
        if (type != null) {
            return "%s == '%s'".formatted(CATEGORY_METADATA, category(type));
        }
        return "%s in ['%s', '%s']".formatted(CATEGORY_METADATA, category(PropertyType.URBAN), category(PropertyType.RURAL));
    }

    static String category(PropertyType type) {
        return "property-" + type.name().toLowerCase();
    }
}
