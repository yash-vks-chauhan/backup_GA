package com.parking.app.repository;

import com.parking.app.model.ParkingSpot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Repository
public interface ParkingSpotRepository extends MongoRepository<ParkingSpot, String> {

    List<ParkingSpot> findByLotId(String lotId);

    // Returned list of spots in lot with available slots > 0 (use in service)
    List<ParkingSpot> findByLotIdAndAvailableGreaterThan(String lotId, int availableThreshold);


}
