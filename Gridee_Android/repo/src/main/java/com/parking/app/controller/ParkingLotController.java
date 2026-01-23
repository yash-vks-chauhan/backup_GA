package com.parking.app.controller;

import com.parking.app.model.ParkingLot;
import com.parking.app.service.ParkingLotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parking-lots")
public class ParkingLotController {

    @Autowired
    private ParkingLotService parkingLotService;

    @GetMapping
    public ResponseEntity<List<ParkingLot>> getAllParkingLots() {
        List<ParkingLot> lots = parkingLotService.getAllParkingLots();
        return ResponseEntity.ok(lots);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ParkingLot> getParkingLotById(@PathVariable String id) {
        ParkingLot lot = parkingLotService.getParkingLotById(id);
        if (lot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(lot);
    }

    @GetMapping("/search/by-name")
    public ResponseEntity<ParkingLot> getParkingLotByName(@RequestParam String name) {
        ParkingLot lot = parkingLotService.getParkingLotByName(name);
        if (lot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(lot);
    }

    @GetMapping("/list/by-names")
    public ResponseEntity<List<String>> getAllParkingLotNames() {
        List<String> names = parkingLotService.getAllParkingLots()
                .stream()
                .map(ParkingLot::getName)
                .collect(Collectors.toList());
        return ResponseEntity.ok(names);
    }

    @PostMapping
    public ResponseEntity<ParkingLot> createParkingLot(@RequestBody ParkingLot parkingLot) {
        ParkingLot created = parkingLotService.createParkingLot(parkingLot);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParkingLot> updateParkingLot(@PathVariable String id, @RequestBody ParkingLot lotDetails) {
        ParkingLot updated = parkingLotService.updateParkingLot(id, lotDetails);
        if (updated == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteParkingLot(@PathVariable String id) {
        parkingLotService.deleteParkingLot(id);
        return ResponseEntity.noContent().build();
    }

}
