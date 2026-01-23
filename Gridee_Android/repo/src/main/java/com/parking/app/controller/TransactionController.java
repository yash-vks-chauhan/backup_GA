package com.parking.app.controller;

import com.parking.app.model.Transactions;
import com.parking.app.service.TransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    @Autowired
    private TransactionService transactionService;

    @PostMapping
    public ResponseEntity<Transactions> createTransaction(@RequestBody Transactions transaction) {
        Transactions created = transactionService.recordTransaction(transaction);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Transactions>> getAllTransactions() {
        return ResponseEntity.ok(transactionService.getAllTransactions());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Transactions> getTransactionById(@PathVariable String id) {
        Transactions transaction = transactionService.getTransactionById(id);
        if (transaction == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(transaction);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Transactions>> getTransactionsByUserId(@PathVariable String userId) {
        return ResponseEntity.ok(transactionService.getTransactionsByUserId(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transactions> updateTransaction(@PathVariable String id, @RequestBody Transactions transactionDetails) {
        Transactions updated = transactionService.updateTransaction(id, transactionDetails);
        if (updated == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable String id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
