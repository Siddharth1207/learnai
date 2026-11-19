package com.learnai.property;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A seller's listing stored in MongoDB. The listing text is also embedded in the vector store
 * ({@code vectorDocumentId}) so buyers can find it with natural-language search.
 *
 * @param transcript English transcript of the seller's voice description (voice listings only)
 * @param videos optional walkthrough videos uploaded after the listing was created
 */
@Document("properties")
public record Property(@Id String id, PropertyDetails details, PropertyStatus status, PropertySource source,
        String transcript, String vectorDocumentId, Instant createdAt, Instant soldAt, String purchaseId,
        List<PropertyVideo> videos) {

    static final String STATUS = "status";
    static final String SOLD_AT = "soldAt";
    static final String PURCHASE_ID = "purchaseId";
    static final String VECTOR_DOCUMENT_ID = "vectorDocumentId";
    static final String CREATED_AT = "createdAt";
    static final String PROPERTY_TYPE = "details.propertyType";
    static final String LISTING_TYPE = "details.listingType";

    /** Keeps older callers that predate videos. */
    public Property(String id, PropertyDetails details, PropertyStatus status, PropertySource source, String transcript,
            String vectorDocumentId, Instant createdAt, Instant soldAt, String purchaseId) {
        this(id, details, status, source, transcript, vectorDocumentId, createdAt, soldAt, purchaseId, List.of());
    }

    Property withVectorDocumentId(String vectorDocumentId) {
        return new Property(id, details, status, source, transcript, vectorDocumentId, createdAt, soldAt, purchaseId,
                videos);
    }

    public Property withVideos(List<PropertyVideo> videos) {
        return new Property(id, details, status, source, transcript, vectorDocumentId, createdAt, soldAt, purchaseId,
                List.copyOf(videos));
    }

    /** Public listing view with the seller contact, exact address and survey number masked. */
    public Property publicView() {
        return new Property(id, details.masked(), status, source, transcript, vectorDocumentId, createdAt, soldAt,
                purchaseId, videos == null ? List.of() : videos);
    }
}
