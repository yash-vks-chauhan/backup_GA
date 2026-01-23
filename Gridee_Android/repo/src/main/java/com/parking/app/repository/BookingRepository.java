package com.parking.app.repository;

import com.parking.app.model.Bookings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends MongoRepository<Bookings, String> {
    List<Bookings> findBySpotId(String spotId);
    List<Bookings> findByUserId(String userId);
    List<Bookings> findByStatus(String status);
    List<Bookings> findBySpotIdAndStatusNot(String spotId, String status);

}
