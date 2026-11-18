package com.learnai.property;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A seller's listing stored in MongoDB. The listing text is also embedded in the vector store
 * ({@code vectorDocumentId}) so buyers can find it with natural-language search.
 *
 * @param transcript English transcript of the seller's voice description (voice listings only)
 */
@Document("properties")
public record Property(@Id String id, PropertyDetails details, PropertyStatus status, PropertySource source,
        String transcript, String vectorDocumentId, Instant createdAt, Instant soldAt, String purchaseId) {

    static final String STATUS = "status";
    static final String SOLD_AT = "soldAt";
    static final String PURCHASE_ID = "purchaseId";
    static final String VECTOR_DOCUMENT_ID = "vectorDocumentId";
    static final String CREATED_AT = "createdAt";
    static final String PROPERTY_TYPE = "details.propertyType";

    Property withVectorDocumentId(String vectorDocumentId) {
        return new Property(id, details, status, source, transcript, vectorDocumentId, createdAt, soldAt, purchaseId);
    }
}
