package com.parking.app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "transactions")
public class Transactions {
    @Id
    private String id;
    private String userId;
    private String type;        // e.g., "payment", "refund"
    private double amount;
    private String method;      // e.g., "credit_card", "paypal"
    private String referenceId; // transaction ref from payment gateway
    private Date timestamp;
    private String status;      // e.g., "pending", "completed", "failed"

    public Transactions() {
        this.timestamp = new Date();
        this.status = "pending";
    }

    // Getters and setters...

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
