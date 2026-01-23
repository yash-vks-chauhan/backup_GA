package com.parking.app.repository;

import com.parking.app.model.ParkingLot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParkingLotRepository extends MongoRepository<ParkingLot, String> {
    ParkingLot findByName(String name);
}
