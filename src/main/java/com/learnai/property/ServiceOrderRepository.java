package com.learnai.property;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ServiceOrderRepository extends MongoRepository<PaidServiceOrder, String> {

    List<PaidServiceOrder> findByPropertyIdOrderByPurchasedAtDesc(String propertyId);
}
