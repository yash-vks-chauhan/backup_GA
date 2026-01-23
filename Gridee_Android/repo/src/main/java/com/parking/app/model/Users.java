package com.parking.app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document(collection = "users")
public class Users {
    @Id
    private String id;
    private String name;
    private String email;
    private String phone;
    private List<String> vehicleNumbers;
    private boolean firstUser;
    private int walletCoins;
    private Date createdAt;
    private String passwordHash;  // Store hashed password securely

    public Users() {
        this.createdAt = new Date();
        this.walletCoins = 0;
        this.firstUser = true;
    }

    // Getters and setters (include passwordHash)

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public List<String> getVehicleNumbers() {
        return vehicleNumbers;
    }

    public void setVehicleNumbers(List<String> vehicleNumbers) {
        this.vehicleNumbers = vehicleNumbers;
    }

    public boolean isFirstUser() { return firstUser; }
    public void setFirstUser(boolean firstUser) { this.firstUser = firstUser; }

    public int getWalletCoins() { return walletCoins; }
    public void setWalletCoins(int walletCoins) { this.walletCoins = walletCoins; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

}
