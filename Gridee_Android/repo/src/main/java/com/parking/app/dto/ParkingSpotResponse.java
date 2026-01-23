package com.parking.app.dto;

import java.util.List;

public class ParkingSpotResponse {
    private String id;
    private String name;
    private String address;
    private double latitude;
    private double longitude;
    private double pricePerHour;
    private int availableSpots;
    private int totalSpots;
    private double distance; // in meters
    private float rating;
    private int reviewCount;
    private List<String> amenities;
    private List<String> photos;

    // Constructors
    public ParkingSpotResponse() {}

    public ParkingSpotResponse(String id, String name, String address, double latitude, double longitude,
                              double pricePerHour, int availableSpots, int totalSpots, double distance,
                              float rating, int reviewCount, List<String> amenities, List<String> photos) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.pricePerHour = pricePerHour;
        this.availableSpots = availableSpots;
        this.totalSpots = totalSpots;
        this.distance = distance;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.amenities = amenities;
        this.photos = photos;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getPricePerHour() { return pricePerHour; }
    public void setPricePerHour(double pricePerHour) { this.pricePerHour = pricePerHour; }

    public int getAvailableSpots() { return availableSpots; }
    public void setAvailableSpots(int availableSpots) { this.availableSpots = availableSpots; }

    public int getTotalSpots() { return totalSpots; }
    public void setTotalSpots(int totalSpots) { this.totalSpots = totalSpots; }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }

    public List<String> getAmenities() { return amenities; }
    public void setAmenities(List<String> amenities) { this.amenities = amenities; }

    public List<String> getPhotos() { return photos; }
    public void setPhotos(List<String> photos) { this.photos = photos; }
}
