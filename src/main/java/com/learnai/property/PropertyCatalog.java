package com.learnai.property;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Reads the listings stored in MongoDB, independently of the LLM/vector store: hard filters (type, listing type,
 * price range, radius) are translated to a Mongo query and the buyer's wording is matched against the listing text.
 * The catalog is also the source for the market-research aggregates. Everything here works with the vector store
 * and Gemini disabled.
 */
@Component
public class PropertyCatalog {

    /** Caps the documents loaded for in-memory ranking and aggregates. */
    static final int MAX_COHORT = 500;

    private static final List<String> KEYWORD_FIELDS = List.of("details.title", "details.description",
            "details.address.city", "details.address.locality", "details.urban.buildingType",
            "details.urban.apartmentName", "details.urban.amenities", "details.urban.furnishing",
            "details.rural.landUse", "details.rural.crop", "details.rural.soilType", "details.rural.waterSource",
            "details.rural.ownershipType");

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "with", "near", "from", "under", "over", "below", "above", "within",
            "any", "all", "some", "please", "show", "find", "want", "need", "looking", "look", "give", "about",
            "property", "properties", "listing", "listings", "house", "home", "place", "budget",
            "buy", "rent", "sale", "sell", "price", "cost", "max", "min", "per", "into", "that", "this");

    private final MongoTemplate mongo;

    public PropertyCatalog(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    /**
     * Listings that pass the hard filters, most relevant to the query first. When no listing shares a keyword with
     * the request, the newest listings that pass the filters are returned so the buyer still sees options.
     */
    public List<Match> search(PropertySearch search, int limit) {
        List<Property> candidates = candidates(search);
        return candidates.stream()
                .map(property -> new Match(property, relevance(search.query(), property)))
                .sorted(Comparator.comparingDouble(Match::relevance).reversed()
                        .thenComparing(m -> m.property().createdAt(), Comparator.reverseOrder()))
                .limit(limit)
                .toList();
    }

    /** Market aggregates for the available listings that match the filters, ignoring the query wording. */
    public MarketResearch research(PropertySearch search) {
        Query query = filtersQuery(search);
        query.with(Sort.by(Sort.Direction.DESC, Property.CREATED_AT)).limit(MAX_COHORT);
        List<Property> cohort = mongo.find(query, Property.class).stream()
                .filter(property -> withinRadius(property, search))
                .toList();
        return aggregate(cohort, search.listingType() == ListingType.RENT);
    }

    private List<Property> candidates(PropertySearch search) {
        List<String> terms = terms(search.query());
        if (!terms.isEmpty()) {
            Query keywordQuery = filtersQuery(search);
            List<Criteria> keywordCriteria = KEYWORD_FIELDS.stream()
                    .map(field -> where(field).regex(anyOf(terms), "i"))
                    .toList();
            keywordQuery.addCriteria(new Criteria().orOperator(keywordCriteria));
            keywordQuery.with(Sort.by(Sort.Direction.DESC, Property.CREATED_AT)).limit(MAX_COHORT);
            List<Property> found = mongo.find(keywordQuery, Property.class).stream()
                    .filter(property -> withinRadius(property, search))
                    .toList();
            if (!found.isEmpty()) {
                return found;
            }
        }
        Query query = filtersQuery(search);
        query.with(Sort.by(Sort.Direction.DESC, Property.CREATED_AT)).limit(MAX_COHORT);
        return mongo.find(query, Property.class).stream()
                .filter(property -> withinRadius(property, search))
                .toList();
    }

    /**
     * Marks listings that are for sale. Listings created before rent existed have no {@code listingType} field.
     */
    static Criteria listingTypeCriteria(ListingType listingType) {
        return listingType == ListingType.SALE
                ? new Criteria().orOperator(where(Property.LISTING_TYPE).is(ListingType.SALE),
                        where(Property.LISTING_TYPE).is(null))
                : where(Property.LISTING_TYPE).is(listingType);
    }

    private static Query filtersQuery(PropertySearch search) {
        Query query = new Query(where(Property.STATUS).is(PropertyStatus.AVAILABLE));
        if (search.propertyType() != null) {
            query.addCriteria(where(Property.PROPERTY_TYPE).is(search.propertyType()));
        }
        if (search.listingType() != null) {
            query.addCriteria(listingTypeCriteria(search.listingType()));
        }
        if (search.minPrice() != null || search.maxPrice() != null) {
            Criteria price = where("details.price");
            if (search.minPrice() != null) {
                price.gte(search.minPrice());
            }
            if (search.maxPrice() != null) {
                price.lte(search.maxPrice());
            }
            query.addCriteria(price);
        }
        return query;
    }

    private static boolean withinRadius(Property property, PropertySearch search) {
        return search.radiusKm() == null || search.near() == null
                || search.near().distanceKm(property.details().coordinates()) <= search.radiusKm();
    }

    /** Fraction of the query terms that appear in the listing text (0..1). */
    static double relevance(String query, Property property) {
        List<String> wanted = terms(query);
        if (wanted.isEmpty()) {
            return 0.0;
        }
        String text = PropertyText.describe(property.details().masked()).toLowerCase(Locale.ROOT);
        long matched = wanted.stream().filter(text::contains).count();
        return Math.round((double) matched / wanted.size() * 100.0) / 100.0;
    }

    /** Lowercase words of at least three letters, without stop words, deduplicated. */
    static List<String> terms(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Set<String> words = new LinkedHashSet<>();
        for (String raw : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (raw.length() >= 3 && !STOP_WORDS.contains(raw)) {
                words.add(raw);
            }
        }
        return List.copyOf(words);
    }

    private static String anyOf(List<String> terms) {
        return terms.stream().map(PropertyCatalog::quote).collect(Collectors.joining("|"));
    }

    /** Escapes regex metacharacters so a buyer's text is matched literally. */
    private static String quote(String term) {
        StringBuilder escaped = new StringBuilder(term.length() + 8);
        for (char c : term.toCharArray()) {
            if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    static MarketResearch aggregate(List<Property> cohort, boolean rent) {
        if (cohort.isEmpty()) {
            return new MarketResearch(0, null, null, null, null, null, null, Map.of(), null);
        }
        List<BigDecimal> prices = cohort.stream().map(p -> p.details().price()).sorted().toList();
        BigDecimal perSqFt = mean(cohort.stream()
                .map(PropertyCatalog::pricePerSqFt)
                .filter(Objects::nonNull)
                .toList());
        BigDecimal deposit = rent ? mean(cohort.stream().map(p -> p.details().deposit())
                .filter(Objects::nonNull).toList()) : null;
        Map<String, Long> byCity = cohort.stream()
                .collect(Collectors.groupingBy(PropertyCatalog::city, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        return new MarketResearch(cohort.size(), mean(prices), median(prices), prices.get(0),
                prices.get(prices.size() - 1), perSqFt, deposit, byCity, null);
    }

    private static String city(Property property) {
        Address address = property.details().address();
        return address == null || address.city() == null || address.city().isBlank() ? "Unknown" : address.city();
    }

    private static BigDecimal pricePerSqFt(Property property) {
        PropertyDetails d = property.details();
        if (d.area() == null || d.area().signum() <= 0 || d.price() == null) {
            return null;
        }
        BigDecimal sqFt = d.area().multiply(squareFeetPerUnit(d.areaUnit()));
        return d.price().divide(sqFt, new MathContext(10));
    }

    private static BigDecimal squareFeetPerUnit(AreaUnit unit) {
        if (unit == null) {
            return BigDecimal.ONE;
        }
        return switch (unit) {
            case SQ_FT -> BigDecimal.ONE;
            case SQ_M -> BigDecimal.valueOf(10.7639);
            case SQ_YD -> BigDecimal.valueOf(9);
            case ACRE -> BigDecimal.valueOf(43560);
            case HECTARE -> BigDecimal.valueOf(107639);
        };
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), new MathContext(10)).setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal median(List<BigDecimal> sorted) {
        int size = sorted.size();
        int middle = size / 2;
        return size % 2 == 1
                ? sorted.get(middle)
                : sorted.get(middle - 1).add(sorted.get(middle))
                        .divide(BigDecimal.valueOf(2), new MathContext(10)).setScale(0, RoundingMode.HALF_UP);
    }

    /** A listing and how many of the buyer's words it matches (0..1). */
    public record Match(Property property, double relevance) {
    }
}
