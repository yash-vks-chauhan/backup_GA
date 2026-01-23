package com.parking.app.service;

import com.parking.app.dto.ParkingSpotResponse;
import com.parking.app.model.ParkingLot;
import com.parking.app.model.ParkingSpot;
import com.parking.app.repository.ParkingLotRepository;
import com.parking.app.repository.ParkingSpotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

@Service
public class ParkingDiscoveryService {

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private ParkingSpotRepository parkingSpotRepository;

    public List<ParkingSpotResponse> getDiscoveryParkingSpots() {
        List<ParkingLot> parkingLots = parkingLotRepository.findAll();
        List<ParkingSpotResponse> responses = new ArrayList<>();
        Random random = new Random();

        for (ParkingLot lot : parkingLots) {
            // Get parking spots for this lot
            List<ParkingSpot> spots = parkingSpotRepository.findByLotId(lot.getId());
            
            // Calculate total available spots for this lot
            int totalAvailable = spots.stream().mapToInt(ParkingSpot::getAvailable).sum();
            int totalCapacity = spots.stream().mapToInt(ParkingSpot::getCapacity).sum();
            
            // Create a combined response for the lot
            ParkingSpotResponse response = new ParkingSpotResponse();
            response.setId(lot.getId());
            response.setName(lot.getName());
            response.setAddress(lot.getLocation());
            response.setLatitude(lot.getLatitude());
            response.setLongitude(lot.getLongitude());
            response.setPricePerHour(2.5); // ₹2.5 per hour as requested
            response.setAvailableSpots(totalAvailable);
            response.setTotalSpots(totalCapacity);
            
            // Mock distance calculation (200m to 800m from user)
            response.setDistance(200 + random.nextInt(600));
            
            // Mock ratings (3.5 to 4.8)
            response.setRating(3.5f + random.nextFloat() * 1.3f);
            response.setReviewCount(15 + random.nextInt(200));
            
            // Add amenities based on location name
            List<String> amenities = generateAmenities(lot.getName());
            response.setAmenities(amenities);
            
            // Empty photos for now
            response.setPhotos(new ArrayList<>());
            
            responses.add(response);
        }

        return responses;
    }

    private List<String> generateAmenities(String locationName) {
        List<String> allAmenities = Arrays.asList("covered", "security", "cctv", "ev_charging");
        List<String> selectedAmenities = new ArrayList<>();
        
        // All locations have security and CCTV
        selectedAmenities.add("security");
        selectedAmenities.add("cctv");
        
        // Mall and IT hub locations are covered
        if (locationName.toLowerCase().contains("mall") || 
            locationName.toLowerCase().contains("it") ||
            locationName.toLowerCase().contains("metro")) {
            selectedAmenities.add("covered");
        }
        
        // Central locations have EV charging
        if (locationName.toLowerCase().contains("central") || 
            locationName.toLowerCase().contains("connaught") ||
            locationName.toLowerCase().contains("nehru")) {
            selectedAmenities.add("ev_charging");
        }
        
        return selectedAmenities;
    }
}
