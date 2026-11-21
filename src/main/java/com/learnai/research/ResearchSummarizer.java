package com.learnai.research;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.learnai.property.KpiImpact;
import com.learnai.property.LocationKpi;
import com.learnai.property.LocationRecommendation;
import com.learnai.property.Property;

/** RAG step of the paid location report: turns live web snippets into KPI summaries and an overall recommendation. */
@Component
public class ResearchSummarizer {

    private static final Logger log = LoggerFactory.getLogger(ResearchSummarizer.class);

    private static final String KPI_PROMPT = """
            You are a property due-diligence analyst working on Indian real estate.
            You are given web snippets about one KPI near a property's location.
            Reply with exactly two lines and nothing else:
            IMPACT: POSITIVE | NEGATIVE | NEUTRAL | UNCERTAIN
            SUMMARY: at most 55 words, plain sentences, naming the project, authority, distance or date only when a
            snippet states it.

            POSITIVE means an upcoming or existing development that should support demand or values (for example a new
            metro or railway station, bus stand, hospital, school or sanctioned township). NEGATIVE means a risk (for
            example land acquisition, demolition, road widening taking land, flooding, encroachment or a rejected
            permission). NEUTRAL means present but already priced in or of little effect. UNCERTAIN means the snippets do
            not confirm anything about this KPI near this location; then say what could not be confirmed.
            Never invent facts, projects, dates or distances.
            """;

    private static final String RECOMMENDATION_PROMPT = """
            You are a property advisor in India. You are given the researched KPI summaries, their impacts, a
            suitability score (0-100) and a rule-based verdict for one listing.
            Write a recommendation of at most 120 words for the buyer: lead with the verdict, summarise the two or three
            strongest signals with their distance, timing or authority when the summaries state them, name the main
            risks, and end with one practical next step (for example verifying an approval or asking for the RERA
            registration number). Use only the material provided. This is research, not investment advice.
            """;

    private final ObjectProvider<ChatModel> chatModel;

    public ResearchSummarizer(ObjectProvider<ChatModel> chatModel) {
        this.chatModel = chatModel;
    }

    /** Returns null when no chat model is configured, there are no sources, or the model call fails. */
    public KpiAssessment assess(String label, String location, String listingFacts, List<WebSource> sources) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null || sources.isEmpty()) {
            return null;
        }
        try {
            String content = ChatClient.create(model).prompt()
                    .system(KPI_PROMPT)
                    .user("KPI: %s\nLocation: %s\nListing: %s\n\nWeb snippets:\n%s".formatted(label, location,
                            listingFacts, sources(sources)))
                    .call()
                    .content();
            return parse(content);
        }
        catch (RuntimeException e) {
            log.warn("Failed to summarise the {} KPI: {}", label, e.getMessage());
            return null;
        }
    }

    /** Returns null when no chat model is configured, or the model call fails. */
    public String recommendation(Property property, String location, LocationRecommendation verdict, int score,
            List<LocationKpi> kpis) {
        ChatModel model = chatModel.getIfAvailable();
        if (model == null) {
            return null;
        }
        try {
            String summary = ChatClient.create(model).prompt()
                    .system(RECOMMENDATION_PROMPT)
                    .user("Location: %s\nListing: %s\nRule-based verdict: %s\nSuitability score: %d/100\n\nKPIs:\n%s"
                            .formatted(location, PropertyFacts.of(property), verdict, score, kpis(kpis)))
                    .call()
                    .content();
            return summary != null ? summary.trim() : null;
        }
        catch (RuntimeException e) {
            log.warn("Failed to generate the location recommendation: {}", e.getMessage());
            return null;
        }
    }

    /** Parses the strict two-line answer; anything unreadable becomes {@link KpiImpact#UNCERTAIN}. */
    static KpiAssessment parse(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        KpiImpact impact = KpiImpact.UNCERTAIN;
        StringBuilder summary = new StringBuilder();
        for (String line : content.strip().split("\\R")) {
            String trimmed = line.strip();
            String upper = trimmed.toUpperCase(Locale.ROOT);
            if (upper.startsWith("IMPACT:")) {
                String value = upper.substring("IMPACT:".length()).strip();
                impact = switch (value.split("[^A-Z]+")[0]) {
                    case "POSITIVE" -> KpiImpact.POSITIVE;
                    case "NEGATIVE" -> KpiImpact.NEGATIVE;
                    case "NEUTRAL" -> KpiImpact.NEUTRAL;
                    default -> KpiImpact.UNCERTAIN;
                };
            }
            else if (upper.startsWith("SUMMARY:")) {
                summary.append(trimmed.substring("SUMMARY:".length()).strip());
            }
            else if (!summary.isEmpty() && !trimmed.isEmpty()) {
                summary.append(' ').append(trimmed);
            }
        }
        String text = summary.toString().strip();
        if (text.isEmpty()) {
            text = content.strip();
        }
        return new KpiAssessment(impact, text.length() <= 600 ? text : text.substring(0, 600) + "…");
    }

    private static String sources(List<WebSource> sources) {
        return sources.stream()
                .map(source -> "- %s | %s | %s".formatted(source.title(), source.url(), source.snippet()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private static String kpis(List<LocationKpi> kpis) {
        return kpis.stream()
                .map(kpi -> "- %s: %s (%s)".formatted(kpi.label(), kpi.summary(), kpi.impact()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }
}
