package com.parking.app.service;

import com.parking.app.model.ParkingLot;
import com.parking.app.repository.ParkingLotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParkingLotService {

    @Autowired
    private ParkingLotRepository parkingLotRepository;

    @Autowired
    private MongoOperations mongoOperations;

    public ParkingLot createParkingLot(ParkingLot parkingLot) {
        return parkingLotRepository.save(parkingLot);
    }

    public List<ParkingLot> getAllParkingLots() {
        return parkingLotRepository.findAll();
    }

    public ParkingLot getParkingLotById(String id) {
        return parkingLotRepository.findById(id).orElse(null);
    }

    public ParkingLot getParkingLotByName(String name) {
        return parkingLotRepository.findByName(name);
    }

    public ParkingLot updateParkingLot(String id, ParkingLot lotDetails) {
        ParkingLot existing = parkingLotRepository.findById(id).orElse(null);
        if (existing != null) {
            if (lotDetails.getName() != null) existing.setName(lotDetails.getName());
            if (lotDetails.getLocation() != null) existing.setLocation(lotDetails.getLocation());
            if (lotDetails.getTotalSpots() > 0) existing.setTotalSpots(lotDetails.getTotalSpots());
            existing.setAvailableSpots(lotDetails.getAvailableSpots());
            return parkingLotRepository.save(existing);
        }
        return null;
    }

    public void deleteParkingLot(String id) {
        parkingLotRepository.deleteById(id);
    }

    // Parking lot availability updates can be optional or used for overall summary

    public ParkingLot reserveSpot(String lotId) {
        Query query = new Query(Criteria.where("id").is(lotId).and("availableSpots").gt(0));
        Update update = new Update().inc("availableSpots", -1);
        return mongoOperations.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ParkingLot.class);
    }

    public ParkingLot releaseSpot(String lotId) {
        Query query = new Query(Criteria.where("id").is(lotId));
        Update update = new Update().inc("availableSpots", 1);
        return mongoOperations.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), ParkingLot.class);
    }
}
