package com.parking.app.service.booking;

import com.parking.app.constants.BookingStatus;
import com.parking.app.model.Bookings;
import com.parking.app.model.Users;
import com.parking.app.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service responsible for querying and retrieving booking data
 * Filters out incomplete bookings to prevent iOS decoding errors
 */
@Service
public class BookingQueryService {

    private static final Logger logger = LoggerFactory.getLogger(BookingQueryService.class);

    private final BookingRepository bookingRepository;
    private final MongoOperations mongoOperations;
    private final BookingValidationService validationService;

    public BookingQueryService(BookingRepository bookingRepository,
                               MongoOperations mongoOperations,
                               BookingValidationService validationService) {
        this.bookingRepository = bookingRepository;
        this.mongoOperations = mongoOperations;
        this.validationService = validationService;
    }

    /**
     * Get filtered bookings with pagination and filtering by status, lotId, date range
     * Filters out bookings with missing required fields (lotId, spotId, userId, vehicleNumber)
     */
    public List<Bookings> getAllBookingsFiltered(String status, String lotId,
                                                 ZonedDateTime fromDate, ZonedDateTime toDate,
                                                 int page, int size) {
        Query query = new Query();

        if (status != null && !status.isEmpty()) {
            query.addCriteria(Criteria.where(Bookings.FIELD_STATUS).is(status));
        }
        if (lotId != null && !lotId.isEmpty()) {
            query.addCriteria(Criteria.where(Bookings.FIELD_LOT_ID).is(lotId));
        }
        if (fromDate != null) {
            query.addCriteria(Criteria.where(Bookings.FIELD_CHECK_IN_TIME).gte(Date.from(fromDate.toInstant())));
        }
        if (toDate != null) {
            query.addCriteria(Criteria.where(Bookings.FIELD_CHECK_OUT_TIME).lte(Date.from(toDate.toInstant())));
        }

        query.with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, Bookings.FIELD_CREATED_AT)));
        List<Bookings> bookings = mongoOperations.find(query, Bookings.class);

        // ✅ FILTER: Remove bookings with missing required fields
        List<Bookings> filtered = bookings.stream()
                .filter(validationService::hasRequiredFields)
                .collect(Collectors.toList());

        logFilteredBookings(bookings.size(), filtered.size(), "getAllBookingsFiltered");
        return filtered;
    }

    /**
     * Get booking history for user (completed bookings)
     * Filters out bookings with missing required fields
     */
    public List<Bookings> getBookingHistoryByUserId(String userId) {
        Date now = Date.from(ZonedDateTime.now().toInstant());
        Query query = new Query(
                Criteria.where(Bookings.FIELD_USER_ID).is(userId)
                        .and(Bookings.FIELD_STATUS).is(BookingStatus.COMPLETED.name())
                        .and(Bookings.FIELD_CHECK_OUT_TIME).lt(now)
        );
        query.with(Sort.by(Sort.Direction.DESC, Bookings.FIELD_CHECK_OUT_TIME));
        List<Bookings> bookings = mongoOperations.find(query, Bookings.class);

        // ✅ FILTER: Remove bookings with missing required fields
        List<Bookings> filtered = bookings.stream()
                .filter(validationService::hasRequiredFields)
                .collect(Collectors.toList());

        logFilteredBookings(bookings.size(), filtered.size(), "getBookingHistoryByUserId");
        logger.debug("📋 Retrieved {} history bookings for userId={}", filtered.size(), userId);
        return filtered;
    }

    /**
     * Get all bookings (admin use)
     * Filters out bookings with missing required fields
     */
    public List<Bookings> getAllBookings() {
        List<Bookings> bookings = bookingRepository.findAll();

        // ✅ FILTER: Remove bookings with missing required fields
        List<Bookings> filtered = bookings.stream()
                .filter(validationService::hasRequiredFields)
                .collect(Collectors.toList());

        logFilteredBookings(bookings.size(), filtered.size(), "getAllBookings");
        return filtered;
    }

    /**
     * Get booking by ID
     * Returns null if booking not found or missing required fields
     */
    public Bookings getBookingById(String id) {
        Bookings booking = bookingRepository.findById(id).orElse(null);

        if (booking != null && !validationService.hasRequiredFields(booking)) {
            logger.warn("⚠️ Booking {} has missing required fields - filtering out", id);
            return null;
        }

        return booking;
    }

    /**
     * Get all bookings for a user (active and pending)
     * Filters out bookings with missing required fields
     */
    public List<Bookings> getBookingsByUserId(String userId) {
        List<Bookings> bookings = bookingRepository.findByUserId(userId);

        // ✅ FILTER: Remove bookings with missing required fields
        List<Bookings> filtered = bookings.stream()
                .filter(validationService::hasRequiredFields)
                .collect(Collectors.toList());

        logFilteredBookings(bookings.size(), filtered.size(), "getBookingsByUserId");
        logger.debug("📋 Retrieved {} bookings for userId={}", filtered.size(), userId);
        return filtered;
    }

    /**
     * Find bookings by lot ID and time window
     * Filters out bookings with missing required fields
     */
    public List<Bookings> findByLotIdAndTimeWindow(String lotId, ZonedDateTime startTime,
                                                   ZonedDateTime endTime) {
        List<Bookings> bookings = bookingRepository.findByLotIdAndTimeWindow(lotId, startTime, endTime);

        // ✅ FILTER: Remove bookings with missing required fields
        List<Bookings> filtered = bookings.stream()
                .filter(validationService::hasRequiredFields)
                .collect(Collectors.toList());

        logFilteredBookings(bookings.size(), filtered.size(), "findByLotIdAndTimeWindow");
        return filtered;
    }

    /**
     * Get vehicle numbers for a user
     * This method doesn't need filtering as it works with Users entity
     */
    public List<String> getVehicleNumbersByUserId(String userId, Users user) {
        return user.getVehicleNumbers();
    }

    /**
     * Helper method to log filtered bookings for monitoring
     */
    private void logFilteredBookings(int original, int filtered, String method) {
        int removed = original - filtered;
        if (removed > 0) {
            logger.warn("⚠️ Filtered {} incomplete bookings in {} (original={}, valid={})",
                    removed, method, original, filtered);
        } else {
            logger.debug("✅ All {} bookings in {} have required fields", original, method);
        }
    }
}
