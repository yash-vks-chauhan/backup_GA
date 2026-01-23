package com.parking.app.controller;

import com.parking.app.model.ParkingSpot;
import com.parking.app.service.ParkingSpotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parking-spots")
public class ParkingSpotController {

    @Autowired
    private ParkingSpotService parkingSpotService;

    @GetMapping
    public ResponseEntity<List<ParkingSpot>> getAllParkingSpots() {
        List<ParkingSpot> spots = parkingSpotService.getAllParkingSpots();
        return ResponseEntity.ok(spots);
    }

    @GetMapping("/lot/{lotId}")
    public ResponseEntity<List<ParkingSpot>> getParkingSpotsByLot(@PathVariable String lotId) {
        List<ParkingSpot> spots = parkingSpotService.getParkingSpotsByLotId(lotId);
        return ResponseEntity.ok(spots);
    }
    @GetMapping("/fix-zone-names")
    public ResponseEntity<String> fixZoneNames() {
        parkingSpotService.fixAllZoneNames();
        return ResponseEntity.ok("Zone names have been fixed for all parking spots");
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParkingSpot> getParkingSpotById(@PathVariable String id) {
        ParkingSpot spot = parkingSpotService.getParkingSpotById(id);
        if (spot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(spot);
    }

    @PostMapping
    public ResponseEntity<ParkingSpot> createParkingSpot(@RequestBody ParkingSpot spot) {
        ParkingSpot created = parkingSpotService.createParkingSpot(spot);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParkingSpot> updateParkingSpot(@PathVariable String id, @RequestBody ParkingSpot spotDetails) {
        ParkingSpot updated = parkingSpotService.updateParkingSpot(id, spotDetails);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParkingSpot(@PathVariable String id) {
        parkingSpotService.deleteParkingSpot(id);
        return ResponseEntity.noContent().build();
    }

    // Reserve a spot (hold & decrement available count)
    @PostMapping("/{id}/hold")
    public ResponseEntity<ParkingSpot> holdSpot(@PathVariable String id, @RequestParam String userId) {
        ParkingSpot spot = parkingSpotService.holdSpot(id, userId);
        if (spot == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(null);  // no availability
        }
        return ResponseEntity.ok(spot);
    }

    // Release a held spot (increment available count)
    @PostMapping("/{id}/release")
    public ResponseEntity<ParkingSpot> releaseSpot(@PathVariable String id) {
        ParkingSpot spot = parkingSpotService.releaseSpot(id);
        if (spot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(spot);
    }
}
