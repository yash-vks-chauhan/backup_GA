package com.parking.app.service;

import com.parking.app.model.Transactions;
import com.parking.app.repository.TransactionsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class TransactionService {

    @Autowired
    private TransactionsRepository transactionRepository;

    public Transactions recordTransaction(Transactions transaction) {
        transaction.setTimestamp(new Date());
        if (transaction.getStatus() == null) {
            transaction.setStatus("pending");
        }
        return transactionRepository.save(transaction);
    }

    public List<Transactions> getAllTransactions() {
        return transactionRepository.findAll();
    }

    public Transactions getTransactionById(String id) {
        return transactionRepository.findById(id).orElse(null);
    }

    public List<Transactions> getTransactionsByUserId(String userId) {
        return transactionRepository.findByUserId(userId);
    }

    public List<Transactions> getTransactionsByType(String type) {
        return transactionRepository.findByType(type);
    }

    public List<Transactions> getTransactionsByStatus(String status) {
        return transactionRepository.findByStatus(status);
    }

    public Transactions updateTransaction(String id, Transactions transactionDetails) {
        Transactions existing = transactionRepository.findById(id).orElse(null);
        if (existing == null) return null;

        if (transactionDetails.getType() != null) existing.setType(transactionDetails.getType());
        if (transactionDetails.getStatus() != null) existing.setStatus(transactionDetails.getStatus());
        if (transactionDetails.getAmount() != 0) existing.setAmount(transactionDetails.getAmount());
        if (transactionDetails.getMethod() != null) existing.setMethod(transactionDetails.getMethod());
        if (transactionDetails.getReferenceId() != null) existing.setReferenceId(transactionDetails.getReferenceId());

        return transactionRepository.save(existing);
    }

    public void deleteTransaction(String id) {
        transactionRepository.deleteById(id);
    }
}
