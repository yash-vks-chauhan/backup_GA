package com.parking.app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document(collection = "wallets")
public class Wallet {

    @Id
    private String id;

    private String userId;              // Associated user
    private double balance;             // Current wallet balance
    private Date lastUpdated;           // Last update timestamp
    private List<TransactionRef> transactions; // References to transactions

    public Wallet() {
        this.balance = 0;
        this.lastUpdated = new Date();
    }

    // --- Nested static class for transaction references ---
    public static class TransactionRef {
        private String referenceId;   // same as Transactions.referenceId
        private String type;          // e.g., "payment", "refund"
        private double amount;        // amount change
        private String status;        // e.g., "pending", "completed"

        public TransactionRef() {}

        public String getReferenceId() { return referenceId; }
        public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    // --- Getters and Setters ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public double getBalance() { return balance; }
    public void setBalance(double balance) { this.balance = balance; }

    public Date getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(Date lastUpdated) { this.lastUpdated = lastUpdated; }

    public List<TransactionRef> getTransactions() { return transactions; }
    public void setTransactions(List<TransactionRef> transactions) { this.transactions = transactions; }
}
