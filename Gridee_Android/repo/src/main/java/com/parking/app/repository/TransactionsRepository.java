package com.parking.app.repository;

import com.parking.app.model.Transactions;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionsRepository extends MongoRepository<Transactions, String> {
    List<Transactions> findByUserId(String userId);
    List<Transactions> findByType(String type);
    List<Transactions> findByStatus(String status);
}
