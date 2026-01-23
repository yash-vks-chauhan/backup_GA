package com.parking.app.controller;

import com.parking.app.dto.ParkingSpotResponse;
import com.parking.app.service.ParkingDiscoveryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/discovery")
public class ParkingDiscoveryController {

    @Autowired
    private ParkingDiscoveryService parkingDiscoveryService;

    @GetMapping("/parking-spots")
    public ResponseEntity<List<ParkingSpotResponse>> getDiscoveryParkingSpots() {
        List<ParkingSpotResponse> spots = parkingDiscoveryService.getDiscoveryParkingSpots();
        return ResponseEntity.ok(spots);
    }

    @GetMapping("/parking-spots/search")
    public ResponseEntity<List<ParkingSpotResponse>> searchParkingSpots(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Double maxDistance,
            @RequestParam(required = false) Boolean availableOnly) {
        
        List<ParkingSpotResponse> spots = parkingDiscoveryService.getDiscoveryParkingSpots();
        
        // Apply filters (basic implementation for now)
        if (query != null && !query.isEmpty()) {
            spots = spots.stream()
                    .filter(spot -> spot.getName().toLowerCase().contains(query.toLowerCase()) ||
                                  spot.getAddress().toLowerCase().contains(query.toLowerCase()))
                    .collect(java.util.stream.Collectors.toList());
        }
        
        if (maxPrice != null) {
            spots = spots.stream()
                    .filter(spot -> spot.getPricePerHour() <= maxPrice)
                    .collect(java.util.stream.Collectors.toList());
        }
        
        if (maxDistance != null) {
            spots = spots.stream()
                    .filter(spot -> spot.getDistance() <= maxDistance)
                    .collect(java.util.stream.Collectors.toList());
        }
        
        if (availableOnly != null && availableOnly) {
            spots = spots.stream()
                    .filter(spot -> spot.getAvailableSpots() > 0)
                    .collect(java.util.stream.Collectors.toList());
        }
        
        return ResponseEntity.ok(spots);
    }
}
