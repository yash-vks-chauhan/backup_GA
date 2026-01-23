package com.parking.app.service;

import com.parking.app.model.ParkingSpot;
import com.parking.app.repository.ParkingSpotRepository;
import com.parking.app.repository.ParkingLotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ParkingSpotService {

    @Autowired
    private ParkingSpotRepository parkingSpotRepository;

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private MongoOperations mongoOperations;

    public ParkingSpot createParkingSpot(ParkingSpot spot) {
        if (spot.getAvailable() == 0) {
            spot.setAvailable(spot.getCapacity());
        }

        // ✅ SET PROPER ZONE NAME when creating
        setProperZoneName(spot);

        return parkingSpotRepository.save(spot);
    }

    public List<ParkingSpot> getAllParkingSpots() {
        List<ParkingSpot> spots = parkingSpotRepository.findAll();

        // ✅ ENSURE ALL SPOTS HAVE PROPER ZONE NAMES
        return spots.stream()
                .map(this::ensureProperZoneName)
                .collect(Collectors.toList());
    }

    public List<ParkingSpot> getParkingSpotsByLotId(String lotId) {
        List<ParkingSpot> spots = parkingSpotRepository.findByLotId(lotId);

        // ✅ ENSURE ALL SPOTS HAVE PROPER ZONE NAMES
        return spots.stream()
                .map(this::ensureProperZoneName)
                .collect(Collectors.toList());
    }

    public ParkingSpot getParkingSpotById(String spotId) {
        ParkingSpot spot = parkingSpotRepository.findById(spotId).orElse(null);
        if (spot != null) {
            return ensureProperZoneName(spot);
        }
        return null;
    }

    // ✅ NEW: Helper method to set proper zone names
    private void setProperZoneName(ParkingSpot spot) {
        if (spot.getZoneName() == null ||
                spot.getZoneName().isEmpty() ||
                spot.getZoneName().equals("nil") ||
                spot.getZoneName().equals("null")) {

            String spotId = spot.getId();
            String zoneName = generateZoneNameFromId(spotId);
            spot.setZoneName(zoneName);
        }
    }

    // ✅ NEW: Helper method to ensure proper zone names (with potential save)
    private ParkingSpot ensureProperZoneName(ParkingSpot spot) {
        boolean needsUpdate = false;

        if (spot.getZoneName() == null ||
                spot.getZoneName().isEmpty() ||
                spot.getZoneName().equals("nil") ||
                spot.getZoneName().equals("null")) {

            String zoneName = generateZoneNameFromId(spot.getId());
            spot.setZoneName(zoneName);
            needsUpdate = true;
        }

        // ✅ UPDATE DATABASE if zone name was missing
        if (needsUpdate) {
            System.out.println("🔧 Updating zone name for spot " + spot.getId() + " to: " + spot.getZoneName());
            parkingSpotRepository.save(spot);
        }

        return spot;
    }

    // ✅ NEW: Generate proper zone names based on spot ID
    private String generateZoneNameFromId(String spotId) {
        if (spotId == null || spotId.isEmpty()) {
            return "Unknown Zone";
        }

        // Handle your specific ID patterns
        switch (spotId.toLowerCase()) {
            case "ps1":
                return "Main Parking Zone";
            case "ps2":
                return "Secondary Parking Zone";
            case "ps3":
                return "Tertiary Parking Zone";
            default:
                // Generic pattern for other IDs
                if (spotId.toLowerCase().startsWith("ps")) {
                    String number = spotId.substring(2);
                    return "Parking Zone " + number.toUpperCase();
                } else if (spotId.contains("-")) {
                    // Handle IDs like "zone1-area2"
                    String[] parts = spotId.split("-");
                    return parts[0].substring(0, 1).toUpperCase() + parts[0].substring(1) + " Area";
                } else {
                    // Generic fallback
                    return "Zone " + spotId.toUpperCase();
                }
        }
    }

    // ✅ NEW: Method to fix all existing spots in database
    public void fixAllZoneNames() {
        System.out.println("🔧 Starting zone name fix for all parking spots...");
        List<ParkingSpot> allSpots = parkingSpotRepository.findAll();

        int updatedCount = 0;
        for (ParkingSpot spot : allSpots) {
            if (spot.getZoneName() == null ||
                    spot.getZoneName().equals("nil") ||
                    spot.getZoneName().equals("null") ||
                    spot.getZoneName().isEmpty()) {

                String newZoneName = generateZoneNameFromId(spot.getId());
                spot.setZoneName(newZoneName);
                parkingSpotRepository.save(spot);
                updatedCount++;

                System.out.println("✅ Updated spot " + spot.getId() + " with zone name: " + newZoneName);
            }
        }

        System.out.println("🎉 Zone name fix complete! Updated " + updatedCount + " parking spots.");
    }

    public ParkingSpot updateParkingSpot(String spotId, ParkingSpot spotDetails) {
        ParkingSpot existingSpot = parkingSpotRepository.findById(spotId).orElse(null);
        if (existingSpot != null) {
            if (spotDetails.getLotId() != null) existingSpot.setLotId(spotDetails.getLotId());
            if (spotDetails.getZoneName() != null) existingSpot.setZoneName(spotDetails.getZoneName());
            if (spotDetails.getCapacity() > 0) existingSpot.setCapacity(spotDetails.getCapacity());
            if (spotDetails.getAvailable() >= 0) existingSpot.setAvailable(spotDetails.getAvailable());
            return parkingSpotRepository.save(existingSpot);
        }
        return null;
    }

    public void deleteParkingSpot(String spotId) {
        parkingSpotRepository.deleteById(spotId);
    }

    // ... rest of your existing methods (holdSpot, releaseSpot, etc.)

    public ParkingSpot holdSpot(String spotId, String userId) {
        Query spotQuery = new Query(Criteria.where("_id").is(spotId).and("available").gt(0));
        Update holdUpdate = new Update()
                .inc("available", -1)
                .set("heldBy", userId)
                .set("heldAt", new Date());

        return mongoOperations.findAndModify(spotQuery, holdUpdate,
                FindAndModifyOptions.options().returnNew(true), ParkingSpot.class);
    }

    public ParkingSpot releaseSpot(String spotId) {
        Query spotQuery = new Query(Criteria.where("_id").is(spotId));
        Update releaseUpdate = new Update()
                .inc("available", 1)
                .unset("heldBy")
                .unset("heldAt");

        return mongoOperations.findAndModify(spotQuery, releaseUpdate,
                FindAndModifyOptions.options().returnNew(true), ParkingSpot.class);
    }
}