package com.learnai.property;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PurchaseRepository extends MongoRepository<Purchase, String> {

    Optional<Purchase> findByPropertyId(String propertyId);
}
