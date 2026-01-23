package com.parking.app.repository;

import com.parking.app.model.Users;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<Users, String> {

    Optional<Users> findByEmail(String email);

    Optional<Users> findByPhone(String phone);

    Optional<Users> findByVehicleNumbers(String vehicleNumbers);

    List<Users> findByNameContainingIgnoreCase(String nameFragment);

    List<Users> findByFirstUser(boolean firstUser);

    List<Users> findByWalletCoins(int walletCoins);

    // Useful existence checks
    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByVehicleNumbers(String vehicleNumbers);
}
