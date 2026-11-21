package com.learnai.research;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

import com.learnai.property.KpiImpact;
import com.learnai.property.ListingType;
import com.learnai.property.LocationInsights;
import com.learnai.property.LocationKpi;
import com.learnai.property.LocationRecommendation;
import com.learnai.property.Property;

/**
 * Real-time location research for the paid {@link com.learnai.property.PaidServiceCode#LOCATION_INSIGHTS} report.
 * Each KPI (transport projects, roads, water, power, education, healthcare, construction, planning risks) triggers
 * live web searches — including municipal and state government domains — and the snippets are summarised by Gemini.
 * The buy/rent/hold verdict is rule-based so the report still works without a chat model; without
 * {@code TAVILY_API_KEY} the report answers 503 instead of inventing data.
 */
@Service
public class LocationResearchService {

    private static final Logger log = LoggerFactory.getLogger(LocationResearchService.class);
    private static final ZoneOffset INDIA = ZoneOffset.ofHoursMinutes(5, 30);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy").withLocale(Locale.ENGLISH);
    private static final List<String> GOVERNMENT_DOMAINS = List.of("gov.in", "nic.in");
    private static final Set<String> LISTING_DOMAINS = Set.of("magicbricks.com", "99acres.com", "housing.com",
            "nobroker.in", "makaan.com", "olx.in", "squareyards.com", "commonfloor.com", "proptiger.com", "quikr.com",
            "facebook.com", "youtube.com");
    private static final List<String> POSITIVE_WORDS = List.of("approved", "sanctioned", "inaugurat", "new metro",
            "metro station", "railway station", "bus stand", "bus terminal", "hospital", "medical college", "school",
            "university", "flyover", "ring road", "highway", "smart city", "township", "water supply", "sewage plant",
            "upgrade", "development plan", "green light", "tender");
    private static final List<String> NEGATIVE_WORDS = List.of("land acquisition", "demolition", "road widening",
            "widening", "flood", "flooding", "encroach", "cancelled", "canceled", "rejected", "stay order", "sewage",
            "slum", "pollution", "encroachment", "dilapidated", "collapse", "no objection rejected", "stop work");
    private static final int MAX_SOURCES_PER_KPI = 5;
    private static final int MAX_TOTAL_SOURCES = 14;

    /** A researched KPI: what to search for, how much it matters for the suitability score, and where to look. */
    record Topic(String key, String label, int weight, String query, boolean governmentSources) {
    }

    static final List<Topic> TOPICS = List.of(
            new Topic("transport", "Transport and connectivity", 3,
                    "new metro station, railway station, bus stand, airport or highway project announced", true),
            new Topic("roads", "Roads and traffic", 2, "road widening, flyover, ring road or realignment plan", true),
            new Topic("water", "Water and sanitation", 2, "water supply, sewerage, drainage or solid waste project", true),
            new Topic("power", "Power and utilities", 1, "power substation, electricity transmission or street lighting upgrade", false),
            new Topic("education", "Schools and colleges", 1, "new school, college or university campus coming up", false),
            new Topic("healthcare", "Hospitals", 1, "new hospital, medical college or primary health centre", false),
            new Topic("construction", "Construction and market", 2,
                    "new residential township, builder launch and property price trend", false),
            new Topic("governance", "Planning and permissions", 3,
                    "RERA registration, building permission, land acquisition, zone change or municipal notice", true));

    private final WebSearchClient webSearch;
    private final ResearchSummarizer summarizer;
    private final Clock clock;
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "location-research");
        thread.setDaemon(true);
        return thread;
    });

    @Autowired
    public LocationResearchService(WebSearchClient webSearch, ResearchSummarizer summarizer) {
        this(webSearch, summarizer, Clock.systemUTC());
    }

    LocationResearchService(WebSearchClient webSearch, ResearchSummarizer summarizer, Clock clock) {
        this.webSearch = webSearch;
        this.summarizer = summarizer;
        this.clock = clock;
    }

    /**
     * Searches the live web for the KPIs around the listing.
     *
     * @throws ResearchNotConfiguredException when no search API key is configured
     */
    public LocationInsights research(Property property) {
        if (!webSearch.isEnabled()) {
            throw new ResearchNotConfiguredException(
                    "The location report needs a web search API key. Set TAVILY_API_KEY to enable it.");
        }
        String location = PropertyFacts.location(property.details());
        String facts = PropertyFacts.of(property);
        Instant now = clock.instant();

        List<LocationKpi> kpis = TOPICS.stream()
                .map(topic -> CompletableFuture.supplyAsync(() -> research(topic, location, facts), executor))
                .map(CompletableFuture::join)
                .toList();

        int score = suitability(kpis);
        LocationRecommendation recommendation = recommendation(kpis, score, property.details().listingType());
        String summary = summarizer.recommendation(property, location, recommendation, score, kpis);
        if (summary == null || summary.isBlank()) {
            summary = fallbackRecommendation(kpis, recommendation, score, location, now);
        }
        return new LocationInsights(location, now, recommendation, score, summary, kpis, distinctSources(kpis));
    }

    private LocationKpi research(Topic topic, String location, String facts) {
        try {
            List<WebSource> sources = new ArrayList<>(
                    search(topic.query() + " near " + location + ", India", List.of()));
            if (topic.governmentSources()) {
                sources.addAll(search(topic.query() + " " + location + " government notification", GOVERNMENT_DOMAINS));
            }
            List<WebSource> relevant = relevant(sources);
            KpiAssessment assessment = summarizer.assess(topic.label(), location, facts, relevant);
            if (assessment == null) {
                return new LocationKpi(topic.key(), topic.label(), heuristicImpact(relevant),
                        sourceSummary(relevant), relevant);
            }
            return new LocationKpi(topic.key(), topic.label(), assessment.impact(), assessment.summary(), relevant);
        }
        catch (RuntimeException e) {
            if (e instanceof ResearchNotConfiguredException) {
                throw e;
            }
            log.warn("Location research failed for the {} KPI: {}", topic.label(), e.getMessage());
            return new LocationKpi(topic.key(), topic.label(), KpiImpact.UNCERTAIN,
                    "The web search for this KPI could not be completed.", List.of());
        }
    }

    private List<WebSource> search(String query, List<String> domains) {
        try {
            return webSearch.search(query, domains);
        }
        catch (ResearchNotConfiguredException e) {
            throw e;
        }
        catch (RuntimeException e) {
            log.warn("Web search failed for '{}': {}", query, e.getMessage());
            return List.of();
        }
    }

    /** Drops listing portals and duplicate URLs, keeping the best few sources for one KPI. */
    static List<WebSource> relevant(List<WebSource> sources) {
        Map<String, WebSource> unique = new LinkedHashMap<>();
        for (WebSource source : sources) {
            if (source.url() == null || isListingSite(source.url())) {
                continue;
            }
            unique.putIfAbsent(source.url(), source);
            if (unique.size() == MAX_SOURCES_PER_KPI) {
                break;
            }
        }
        return List.copyOf(unique.values());
    }

    private static boolean isListingSite(String url) {
        String host = host(url);
        return LISTING_DOMAINS.stream().anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
    }

    private static String host(String url) {
        try {
            String host = java.net.URI.create(url).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        }
        catch (IllegalArgumentException e) {
            return "";
        }
    }

    /** Keeps one copy of each source across the report so the response stays small. */
    private static List<WebSource> distinctSources(List<LocationKpi> kpis) {
        Map<String, WebSource> unique = new LinkedHashMap<>();
        for (LocationKpi kpi : kpis) {
            for (WebSource source : kpi.sources()) {
                unique.putIfAbsent(source.url(), source);
                if (unique.size() == MAX_TOTAL_SOURCES) {
                    return List.copyOf(unique.values());
                }
            }
        }
        return List.copyOf(unique.values());
    }

    /** Rule-based impact used when no chat model is configured: risks win, then confirmed development. */
    static KpiImpact heuristicImpact(List<WebSource> sources) {
        String text = sources.stream()
                .map(source -> (source.title() == null ? "" : source.title()) + " "
                        + (source.snippet() == null ? "" : source.snippet()))
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return KpiImpact.UNCERTAIN;
        }
        if (NEGATIVE_WORDS.stream().anyMatch(text::contains)) {
            return KpiImpact.NEGATIVE;
        }
        if (POSITIVE_WORDS.stream().anyMatch(text::contains)) {
            return KpiImpact.POSITIVE;
        }
        return KpiImpact.UNCERTAIN;
    }

    /** Keeps the report useful without an LLM by quoting the best snippet. */
    static String sourceSummary(List<WebSource> sources) {
        if (sources.isEmpty()) {
            return "No public sources were found for this KPI near the location.";
        }
        WebSource first = sources.get(0);
        String snippet = first.snippet() == null ? "" : first.snippet().strip();
        if (snippet.length() > 260) {
            snippet = snippet.substring(0, 260) + "…";
        }
        return ("Latest public source: " + first.title() + ". " + snippet).strip();
    }

    static int suitability(List<LocationKpi> kpis) {
        int score = 50;
        for (LocationKpi kpi : kpis) {
            int weight = weight(kpi.key());
            score += switch (kpi.impact()) {
                case POSITIVE -> 3 * weight;
                case NEGATIVE -> -4 * weight;
                default -> 0;
            };
        }
        return Math.max(5, Math.min(95, score));
    }

    private static int weight(String key) {
        return TOPICS.stream().filter(topic -> topic.key().equals(key)).mapToInt(Topic::weight).findFirst().orElse(1);
    }

    /** A negative roads or planning signal (acquisition, widening, stay order) always means wait. */
    static LocationRecommendation recommendation(List<LocationKpi> kpis, int score, ListingType listingType) {
        boolean dealBreaker = kpis.stream().anyMatch(kpi -> kpi.impact() == KpiImpact.NEGATIVE
                && (kpi.key().equals("roads") || kpi.key().equals("governance")));
        if (dealBreaker) {
            return LocationRecommendation.HOLD;
        }
        boolean rent = listingType == ListingType.RENT;
        if (score < (rent ? 55 : 65)) {
            return LocationRecommendation.HOLD;
        }
        return rent ? LocationRecommendation.RENT : LocationRecommendation.BUY;
    }

    private static String fallbackRecommendation(List<LocationKpi> kpis, LocationRecommendation recommendation,
            int score, String location, Instant now) {
        String positive = kpis.stream()
                .filter(kpi -> kpi.impact() == KpiImpact.POSITIVE)
                .map(LocationKpi::label)
                .collect(Collectors.joining(", "));
        String negative = kpis.stream()
                .filter(kpi -> kpi.impact() == KpiImpact.NEGATIVE)
                .map(LocationKpi::label)
                .collect(Collectors.joining(", "));
        String uncertain = kpis.stream()
                .filter(kpi -> kpi.impact() == KpiImpact.UNCERTAIN)
                .map(LocationKpi::label)
                .collect(Collectors.joining(", "));
        StringBuilder text = new StringBuilder("Suitability score %d/100, verdict %s for %s."
                .formatted(score, recommendation, location));
        text.append(positive.isEmpty() ? " No confirmed positive development was found."
                : " Supporting signals: " + positive + ".");
        if (!negative.isEmpty()) {
            text.append(" Risks to check: ").append(negative).append('.');
        }
        if (!uncertain.isEmpty()) {
            text.append(" Not confirmed by public sources: ").append(uncertain).append('.');
        }
        text.append(" Research from public web sources as of ")
                .append(DAY.format(now.atOffset(INDIA)))
                .append("; verify approvals and the RERA registration before paying a token amount. ")
                .append("This is research, not investment advice.");
        return text.toString();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
