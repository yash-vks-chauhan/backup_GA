//package com.parking.app.service;
//
//import com.parking.app.model.Bookings;
//import com.parking.app.model.ParkingSpot;
//import com.parking.app.model.Users;
//import com.parking.app.repository.BookingRepository;
//import com.parking.app.repository.UserRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.data.mongodb.core.FindAndModifyOptions;
//import org.springframework.data.mongodb.core.MongoOperations;
//import org.springframework.data.mongodb.core.query.Criteria;
//import org.springframework.data.mongodb.core.query.Query;
//import org.springframework.data.mongodb.core.query.Update;
//import org.springframework.stereotype.Service;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Base64;
//import java.util.Date;
//import java.util.List;
//
//@Service
//public class BookingService {
//
//    @Autowired
//    private BookingRepository bookingRepository;
//
//    @Autowired
//    private UserRepository userRepository;
//
//    @Autowired
//    private MongoOperations mongoOperations;
//
//    /**
//     * Start booking: decrement spot availability atomically if available > 0.
//     * Now accepts user-requested checkIn and checkOut times.
//     */
//    public Bookings startBooking(String spotId, String userId, String lotId, Date checkInTime, Date checkOutTime) {
//        if (checkInTime == null || checkOutTime == null) {
//            throw new IllegalArgumentException("Check-in and Check-out times are required.");
//        }
//        if (!checkOutTime.after(checkInTime)) {
//            throw new IllegalArgumentException("Check-out time must be after Check-in time.");
//        }
//
//        Query spotQuery = new Query(Criteria.where("_id").is(spotId).and("available").gt(0));
//        Update decUpdate = new Update().inc("available", -1);
//
//        ParkingSpot updatedSpot = mongoOperations.findAndModify(
//                spotQuery, decUpdate, FindAndModifyOptions.options().returnNew(true), ParkingSpot.class);
//
//        if (updatedSpot == null) {
//            // No spots available
//            return null;
//        }
//
//        long durationMs = checkOutTime.getTime() - checkInTime.getTime();
//        double hoursParked = Math.ceil(durationMs / (1000.0 * 60 * 60));
//        double amount = hoursParked * 2.5;
//
//        Bookings booking = new Bookings();
//        booking.setSpotId(spotId);
//        booking.setUserId(userId);
//        booking.setLotId(lotId);
//        booking.setStatus("pending");
//        booking.setAmount(amount);
//        booking.setCreatedAt(new Date());
//        booking.setCheckInTime(checkInTime);
//        booking.setCheckOutTime(checkOutTime);
//        return bookingRepository.save(booking);
//    }
//
//
//    /**
//     * Confirm booking: set active, check-in time (actual), generate QR code.
//     */
//    public Bookings confirmBooking(String bookingId) {
//        Bookings booking = bookingRepository.findById(bookingId).orElse(null);
//
//        if (booking == null) {
//            System.out.println("Booking not found for id: " + bookingId);
//            return null;
//        }
//
//        if (!"pending".equalsIgnoreCase(booking.getStatus())) {
//            System.out.println("Booking not pending, current status: " + booking.getStatus());
//            return null;
//        }
//
//        booking.setStatus("active");
//        booking.setCheckInTime(new Date());
//
//        long expiryTimestamp = booking.getCheckOutTime() != null
//                ? booking.getCheckOutTime().getTime()
//                : (booking.getCheckInTime() != null
//                ? booking.getCheckInTime().getTime() + 2 * 60 * 60 * 1000
//                : System.currentTimeMillis() + 2 * 60 * 60 * 1000);
//
//        String qrRaw = booking.getId() + "|" + expiryTimestamp;
//        String qrCode = Base64.getEncoder().encodeToString(qrRaw.getBytes(StandardCharsets.UTF_8));
//        booking.setQrCode(qrCode);
//
//        return bookingRepository.save(booking);
//    }
//    /**
//     * Cancel booking and release spot by incrementing availability.
//     */
//    public boolean cancelBooking(String bookingId) {
//        Bookings booking = bookingRepository.findById(bookingId).orElse(null);
//        if (booking == null) return false;
//
//        Update incUpdate = new Update().inc("available", 1);
//        mongoOperations.updateFirst(new Query(Criteria.where("_id").is(booking.getSpotId())), incUpdate, ParkingSpot.class);
//
//        booking.setStatus("cancelled");
//        bookingRepository.save(booking);
//        return true;
//    }
//
//    /**
//     * Check-in: mark actual check-in time, change status to active.
//     */
//    public Bookings checkIn(String bookingId) {
//        Bookings booking = bookingRepository.findById(bookingId).orElse(null);
//        if (booking == null) return null;
//
//        booking.setCheckInTime(new Date());
//        booking.setStatus("active");
//        return bookingRepository.save(booking);
//    }
//
//    /**
//     * Check-out: mark checkout time, release spot, calculate charge, deduct from wallet.
//     */
//    public Bookings checkOut(String bookingId) {
//        Bookings booking = bookingRepository.findById(bookingId).orElse(null);
//        if (booking == null || booking.getCheckInTime() == null) return null;
//
//        Date now = new Date();
//        booking.setCheckOutTime(now);
//        booking.setStatus("completed");
//
//        // Release spot availability
//        Update incUpdate = new Update().inc("available", 1);
//        mongoOperations.updateFirst(new Query(Criteria.where("_id").is(booking.getSpotId())), incUpdate, ParkingSpot.class);
//
//        long durationMs = now.getTime() - booking.getCheckInTime().getTime();
//        double hoursParked = Math.ceil(durationMs / (1000.0 * 60 * 60));
//        double baseCharge = hoursParked * 2.5;
//
//        double penalty = 0;
//        if (booking.getCheckOutTime() != null && booking.getCheckOutTime().after(booking.getCheckOutTime())) {
//            long lateMs = now.getTime() - booking.getCheckOutTime().getTime();
//            double lateHours = Math.ceil(lateMs / (1000.0 * 60 * 60));
//            penalty = lateHours * 5;
//        }
//
//        double totalCharge = baseCharge + penalty;
//        booking.setAmount(totalCharge);
//
//        Users user = userRepository.findById(booking.getUserId()).orElse(null);
//        if (user != null) {
//            int currentCoins = user.getWalletCoins();
//            int neededCoins = (int) Math.ceil(totalCharge);
//            if (currentCoins < neededCoins) {
//                throw new RuntimeException("Insufficient wallet coins.");
//            }
//            user.setWalletCoins(currentCoins - neededCoins);
//            userRepository.save(user);
//        }
//
//        return bookingRepository.save(booking);
//    }
//
//    public List<Bookings> getAllBookings() {
//        return bookingRepository.findAll();
//    }
//
//    public Bookings getBookingById(String id) {
//        return bookingRepository.findById(id).orElse(null);
//    }
//
//    public Bookings updateBookingStatus(String id, String status) {
//        Bookings booking = bookingRepository.findById(id).orElse(null);
//        if (booking == null) return null;
//
//        booking.setStatus(status);
//        return bookingRepository.save(booking);
//    }
//
//    public void deleteBooking(String id) {
//        bookingRepository.deleteById(id);
//    }
//    public List<Bookings> getBookingsByUserId(String userId) {
//        return bookingRepository.findByUserId(userId);
//    }
//}
//
//
////package com.parking.app.service;
////
////import com.parking.app.model.Bookings;
////import com.parking.app.repository.BookingRepository;
////import com.parking.app.service.BookingService;
////import org.springframework.beans.factory.annotation.Autowired;
////import org.springframework.stereotype.Service;
////import java.util.Date;
////import java.util.List;
////
////@Service
////public class BookingService{
////
////    @Autowired
////    private BookingRepository bookingRepository;
////
////    @Override
////    public Bookings startBooking(String spotId, String userId, String lotId, Date checkIn, Date checkOut) {
////        // Basic validation
////        if (spotId == null || userId == null || lotId == null || checkIn == null || checkOut == null) {
////            throw new IllegalArgumentException("Required parameters must not be null");
////        }
////        if (checkIn.compareTo(checkOut) >= 0) {
////            throw new IllegalArgumentException("Check-out time must be after check-in time");
////        }
////
////        // Example: Check for overlapping bookings—you may need to customize this logic
////        List<Bookings> overlapping = bookingRepository.findBySpotIdAndStatusNot(spotId, "CANCELLED");
////        if (overlapping.stream().anyMatch(b ->
////                (b.getCheckIn().before(checkOut) && b.getCheckOut().after(checkIn)))) {
////            throw new IllegalArgumentException("Spot already booked during requested time");
////        }
////
////        // Create and save the booking
////        Bookings booking = new Bookings();
////        booking.setSpotId(spotId);
////        booking.setUserId(userId);
////        booking.setLotId(lotId);
////        booking.setCheckIn(checkIn);
////        booking.setCheckOut(checkOut);
////        booking.setStatus("PENDING");
////        return bookingRepository.save(booking);
////    }
////
////    @Override
////    public Bookings confirmBooking(String id) {
////        Bookings booking = bookingRepository.findById(id)
////                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
////        if (!"PENDING".equals(booking.getStatus())) {
////            throw new IllegalArgumentException("Only pending bookings can be confirmed");
////        }
////        booking.setStatus("CONFIRMED");
////        return bookingRepository.save(booking);
////    }
////
////    @Override
////    public boolean cancelBooking(String id) {
////        Bookings booking = bookingRepository.findById(id)
////                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
////        if (booking.getStatus().equals("CANCELLED")) {
////            return false;
////        }
////        booking.setStatus("CANCELLED");
////        bookingRepository.save(booking);
////        return true;
////    }
////
////    @Override
////    public Bookings checkIn(String id) {
////        Bookings booking = bookingRepository.findById(id)
////                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
////        if (!"CONFIRMED".equals(booking.getStatus())) {
////            throw new IllegalArgumentException("Only confirmed bookings can check in");
////        }
////        booking.setStatus("IN_PROGRESS");
////        return bookingRepository.save(booking);
////    }
////
////    @Override
////    public Bookings checkOut(String id) {
////        Bookings booking = bookingRepository.findById(id)
////                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
////        if (!"IN_PROGRESS".equals(booking.getStatus())) {
////            throw new IllegalArgumentException("Only in-progress bookings can check out");
////        }
////        booking.setStatus("COMPLETED");
////        return bookingRepository.save(booking);
////    }
////
////    @Override
////    public List<Bookings> getAllBookings() {
////        return bookingRepository.findAll();
////    }
////
////    @Override
////    public List<Bookings> getBookingsByUserId(String userId) {
////        return bookingRepository.findByUserId(userId);
////    }
////
////    @Override
////    public Bookings getBookingById(String id) {
////        return bookingRepository.findById(id)
////                .orElse(null);
////    }
////
////    @Override
////    public Bookings updateBookingStatus(String id, String status) {
////        Bookings booking = bookingRepository.findById(id)
////                .orElseThrow(() -> new IllegalArgumentException("Booking not found"));
////        if (status == null) {
////            throw new IllegalArgumentException("Status cannot be null");
////        }
////        booking.setStatus(status);
////        return bookingRepository.save(booking);
////    }
////
////    @Override
////    public void deleteBooking(String id) {
////        bookingRepository.deleteById(id);
////    }
////}




package com.parking.app.service;

import com.parking.app.exception.ConflictException;
import com.parking.app.exception.InsufficientFundsException;
import com.parking.app.exception.NotFoundException;
import com.parking.app.exception.IllegalStateException;
import com.parking.app.model.Bookings;
import com.parking.app.model.ParkingSpot;
import com.parking.app.model.Users;
import com.parking.app.repository.BookingRepository;
import com.parking.app.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;

@Service
@Transactional
public class BookingService {

    private static final double HOURLY_RATE = 2.5;
    private static final double PENALTY_RATE = 5.0;
    private static final int DEFAULT_HOLD_HOURS = 2;

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final MongoOperations mongoOperations;

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            UserRepository userRepository,
            MongoOperations mongoOperations
    ) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.mongoOperations = mongoOperations;
    }

    private Bookings findBookingOrThrow(String bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    public Bookings startBooking(String spotId, String userId, String lotId, Date checkInTime, Date checkOutTime, String vehicleNumber) {
        if (checkInTime == null || checkOutTime == null) {
            throw new IllegalArgumentException("Check-in and Check-out times are required.");
        }
        if (!checkOutTime.after(checkInTime)) {
            throw new IllegalArgumentException("Check-out time must be after Check-in time.");
        }

        Query spotQuery = new Query(Criteria.where("_id").is(spotId).and("available").gt(0));
        Update decUpdate = new Update().inc("available", -1);

        ParkingSpot updatedSpot = mongoOperations.findAndModify(
                spotQuery, decUpdate, org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), ParkingSpot.class);

        if (updatedSpot == null) {
            // No spots available
            throw new com.parking.app.exception.ConflictException("No spots available");
        }

        long durationMs = checkOutTime.getTime() - checkInTime.getTime();
        double hoursParked = Math.ceil(durationMs / (1000.0 * 60 * 60));
        double amount = hoursParked * 2.5;

        Bookings booking = new Bookings();
        booking.setSpotId(spotId);
        booking.setUserId(userId);
        booking.setLotId(lotId);
        booking.setStatus("pending");
        booking.setAmount(amount);
        booking.setCreatedAt(new Date());
        booking.setCheckInTime(checkInTime);
        booking.setCheckOutTime(checkOutTime);
        booking.setVehicleNumber(vehicleNumber);
        return bookingRepository.save(booking);
    }


    public List<Bookings> getAllBookingsFiltered(
            String status,
            String lotId,
            String fromDate,
            String toDate,
            int page,
            int size
    ) {
        Query query = new Query();
        if (status != null) query.addCriteria(Criteria.where("status").is(status));
        if (lotId != null) query.addCriteria(Criteria.where("lotId").is(lotId));
        if (fromDate != null) query.addCriteria(Criteria.where("checkInTime").gte(parseDate(fromDate)));
        if (toDate != null) query.addCriteria(Criteria.where("checkOutTime").lte(parseDate(toDate)));
        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return mongoOperations.find(query, Bookings.class);
    }

    private Date parseDate(String date) {
        try {
            return java.util.Date.from(java.time.ZonedDateTime.parse(date).toInstant());
        } catch (Exception e) {
            return null;
        }
    }

    public List<Bookings> getBookingHistoryByUserId(String userId) {
        Date now = new Date();
        Query query = new Query(
                Criteria.where("userId").is(userId)
                        .orOperator(
                                Criteria.where("status").in("completed", "cancelled"),
                                Criteria.where("checkOutTime").lt(now)
                        )
        );
        query.with(Sort.by(Sort.Direction.DESC, "checkOutTime"));
        return mongoOperations.find(query, Bookings.class);
    }

    private void validateTimes(Date checkIn, Date checkOut) {
        if (checkIn == null || checkOut == null) {
            throw new IllegalArgumentException("Check-in and Check-out times are required");
        }
        if (!checkOut.after(checkIn)) {
            throw new IllegalArgumentException("Check-out time must be after Check-in time");
        }
    }

    public List<String> getVehicleNumbersByUserId(String userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        return user.getVehicleNumbers();  // Returns List<String> of vehicle numbers
    }

    private double calculateCharge(Date from, Date to) {
        long durationMs = to.getTime() - from.getTime();
        return Math.ceil(durationMs / (1000.0 * 60 * 60)) * HOURLY_RATE;
    }

    public Bookings confirmBooking(String bookingId) {
        Bookings booking = findBookingOrThrow(bookingId);
        if (!"pending".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Only pending bookings can be confirmed");
        }

        booking.setStatus("active");
        booking.setCheckInTime(new Date());

        long expiryTimestamp = booking.getCheckOutTime() != null
                ? booking.getCheckOutTime().getTime()
                : (booking.getCheckInTime() != null
                ? booking.getCheckInTime().getTime() + DEFAULT_HOLD_HOURS * 60 * 60 * 1000
                : System.currentTimeMillis() + DEFAULT_HOLD_HOURS * 60 * 60 * 1000);

        String qrRaw = booking.getId() + "|" + expiryTimestamp;
        String qrCode = Base64.getEncoder().encodeToString(qrRaw.getBytes(StandardCharsets.UTF_8));
        booking.setQrCode(qrCode);

        return bookingRepository.save(booking);
    }

    public boolean cancelBooking(String bookingId) {
        Bookings booking = findBookingOrThrow(bookingId);
        if ("cancelled".equalsIgnoreCase(booking.getStatus())) {
            return false;
        }

        Update incUpdate = new Update().inc("available", 1);
        mongoOperations.updateFirst(new Query(Criteria.where("_id").is(booking.getSpotId())), incUpdate, ParkingSpot.class);

        booking.setStatus("cancelled");
        bookingRepository.save(booking);
        return true;
    }

    public Bookings checkIn(String bookingId) {
        Bookings booking = findBookingOrThrow(bookingId);
        if (!"active".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Only active bookings can check in");
        }
        booking.setCheckInTime(new Date());
        return bookingRepository.save(booking);
    }

    public Bookings checkOut(String bookingId) {
        Bookings booking = findBookingOrThrow(bookingId);
        if (!"active".equalsIgnoreCase(booking.getStatus())) {
            throw new IllegalStateException("Only active bookings can check out");
        }

        Date now = new Date();
        booking.setStatus("completed");
        booking.setCheckOutTime(now);

        Update incUpdate = new Update().inc("available", 1);
        mongoOperations.updateFirst(new Query(Criteria.where("_id").is(booking.getSpotId())), incUpdate, ParkingSpot.class);

        double baseCharge = calculateCharge(booking.getCheckInTime(), now);
        double penalty = calculatePenalty(booking.getCheckOutTime(), now);
        double totalCost = baseCharge + penalty;
        booking.setAmount(totalCost);

        Users user = userRepository.findById(booking.getUserId())
                .orElseThrow(() -> new NotFoundException("User not found"));

        int neededCoins = (int) Math.ceil(totalCost);
        if (user.getWalletCoins() < neededCoins) {
            throw new InsufficientFundsException();
        }
        user.setWalletCoins(user.getWalletCoins() - neededCoins);
        userRepository.save(user);

        return bookingRepository.save(booking);
    }

    private double calculatePenalty(Date scheduledEnd, Date actualEnd) {
        if (actualEnd.before(scheduledEnd)) {
            return 0;
        }
        long lateMs = actualEnd.getTime() - scheduledEnd.getTime();
        return Math.ceil(lateMs / (1000.0 * 60 * 60)) * PENALTY_RATE;
    }

    public List<Bookings> getAllBookings() {
        return bookingRepository.findAll();
    }

    public Bookings getBookingById(String id) {
        return bookingRepository.findById(id).orElse(null);
    }

    public Bookings updateBookingStatus(String id, String status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        Bookings booking = findBookingOrThrow(id);
        booking.setStatus(status);
        return bookingRepository.save(booking);
    }

    public void deleteBooking(String id) {
        bookingRepository.deleteById(id);
    }

    public List<Bookings> getBookingsByUserId(String userId) {
        System.out.println("BookingService: Searching for bookings with userId: '" + userId + "'");
        List<Bookings> bookings = bookingRepository.findByUserId(userId);
        System.out.println("BookingService: Found " + bookings.size() + " bookings for user: " + userId);
        
        // Debug: Let's also check all bookings to see what user IDs exist
        List<Bookings> allBookings = bookingRepository.findAll();
        System.out.println("BookingService: Total bookings in system: " + allBookings.size());
        for (Bookings booking : allBookings) {
            if (booking.getUserId() != null && booking.getUserId().contains("68dd1cd4")) {
                System.out.println("BookingService: Found matching booking - ID: " + booking.getId() + ", userId: '" + booking.getUserId() + "'");
            }
        }
        
        return bookings;
    }
}
