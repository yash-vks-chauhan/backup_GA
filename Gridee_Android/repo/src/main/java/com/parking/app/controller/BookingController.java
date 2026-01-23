//package com.parking.app.controller;
//
//import com.parking.app.model.Bookings;
//import com.parking.app.service.BookingService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/bookings")
//public class BookingController {
//
//
//    @Autowired
//    private BookingService bookingService;
//
//    private static final SimpleDateFormat ISO_DATE_TIME_FORMAT =
//            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");
//
//    @GetMapping("/users/{userId}/bookings")
//    public ResponseEntity<List<Bookings>> getBookingsByUserId(@PathVariable String userId) {
//        List<Bookings> bookings = bookingService.getBookingsByUserId(userId);
//        return bookings.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(bookings);
//    }
//
//
//    // Start a booking, now accepting optional checkIn and checkOut times as ISO strings
//    @PostMapping("/start")
//    public ResponseEntity<?> startBooking(
//            @RequestParam String spotId,
//            @RequestParam String userId,
//            @RequestParam String lotId,
//            @RequestParam String checkInTime,
//            @RequestParam String checkOutTime) {
//        try {
//            Date checkIn = ISO_DATE_TIME_FORMAT.parse(checkInTime);
//            Date checkOut = ISO_DATE_TIME_FORMAT.parse(checkOutTime);
//
//            Bookings booking = bookingService.startBooking(spotId, userId, lotId, checkIn, checkOut);
//            if (booking == null) {
//                return ResponseEntity.status(HttpStatus.CONFLICT).body("No spots available");
//            }
//            return ResponseEntity.ok(booking);
//        } catch (ParseException e) {
//            return ResponseEntity.badRequest().body("Invalid date-time format. Use yyyy-MM-dd'T'HH:mm");
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.badRequest().body(e.getMessage());
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unable to create booking");
//        }
//    }
//
//    // Confirm booking (same as before)
//    @PostMapping("/{id}/confirm")
//    public ResponseEntity<Bookings> confirmBooking(@PathVariable String id) {
//        Bookings booking = bookingService.confirmBooking(id);
//        if (booking == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//        return ResponseEntity.ok(booking);
//    }
//
//    // Cancel booking (same as before)
//    @PostMapping("/{id}/cancel")
//    public ResponseEntity<Void> cancelBooking(@PathVariable String id) {
//        boolean success = bookingService.cancelBooking(id);
//        if (success) {
//            return ResponseEntity.noContent().build();
//        } else {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//    }
//
//    // Check in (same as before)
//    @PostMapping("/{id}/checkin")
//    public ResponseEntity<Bookings> checkIn(@PathVariable String id) {
//        Bookings booking = bookingService.checkIn(id);
//        if (booking == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//        return ResponseEntity.ok(booking);
//    }
//
//    // Check out (same as before)
//    @PostMapping("/{id}/checkout")
//    public ResponseEntity<Bookings> checkOut(@PathVariable String id) {
//        try {
//            Bookings booking = bookingService.checkOut(id);
//            if (booking == null) {
//                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//            }
//            return ResponseEntity.ok(booking);
//        } catch (RuntimeException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
//        }
//    }
//
//    // Get bookings (same)
//    @GetMapping
//    public ResponseEntity<List<Bookings>> getAllBookings() {
//        List<Bookings> bookings = bookingService.getAllBookings();
//        return ResponseEntity.ok(bookings);
//    }
//    @GetMapping("/users/{userId}")
//    public ResponseEntity<List<Bookings>> getBookingsByUserId(@PathVariable String userId) {
//        List<Bookings> bookings = bookingService.getBookingsByUserId(userId);
//        if (bookings.isEmpty()) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//        return ResponseEntity.ok(bookings);
//    }
//
//    // Get by id (same)
//    @GetMapping("/{id}")
//    public ResponseEntity<Bookings> getBookingById(@PathVariable String id) {
//        Bookings booking = bookingService.getBookingById(id);
//        if (booking == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//        return ResponseEntity.ok(booking);
//    }
//
//    // Update booking status (same but minor fix to accept JSON with just 'status' field)
//    @PutMapping("/{id}")
//    public ResponseEntity<Bookings> updateBookingStatus(@PathVariable String id, @RequestBody Bookings bookingDetails) {
//        if (bookingDetails.getStatus() == null) {
//            return ResponseEntity.badRequest().build();
//        }
//        Bookings updated = bookingService.updateBookingStatus(id, bookingDetails.getStatus());
//        if (updated == null) {
//            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
//        }
//        return ResponseEntity.ok(updated);
//    }
//
//    // Delete booking (same)
//    @DeleteMapping("/{id}")
//    public ResponseEntity<Void> deleteBooking(@PathVariable String id) {
//        bookingService.deleteBooking(id);
//        return ResponseEntity.noContent().build();
//    }
//}


package com.parking.app.controller;

import com.parking.app.exception.*;
import com.parking.app.exception.IllegalStateException;
import com.parking.app.model.Bookings;
import com.parking.app.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;
    private static final SimpleDateFormat ISO_DATE_TIME_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

    @Autowired
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // --- ADMIN/GLOBAL ENDPOINTS ---

    // List all bookings with optional filtering and pagination
    @GetMapping("/bookings")
    public ResponseEntity<List<Bookings>> getAllBookings(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String lotId,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        // Implement filtering and pagination in your service
        List<Bookings> bookings = bookingService.getAllBookingsFiltered(status, lotId, fromDate, toDate, page, size);
        return bookings.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(bookings);
    }

    // --- USER ENDPOINTS ---

    // Start a booking
    @PostMapping("/users/{userId}/bookings/start")
    public ResponseEntity<?> startBooking(
            @PathVariable String userId,
            @RequestParam String spotId,
            @RequestParam String lotId,
            @RequestParam String checkInTime,
            @RequestParam String checkOutTime,
            @RequestParam String vehicleNumber
    ) {
        try {
            Date checkIn = ISO_DATE_TIME_FORMAT.parse(checkInTime);
            Date checkOut = ISO_DATE_TIME_FORMAT.parse(checkOutTime);
            Bookings booking = bookingService.startBooking(spotId, userId, lotId, checkIn, checkOut, vehicleNumber);
            return ResponseEntity.ok(booking);
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("No spots available");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Confirm booking
    @PostMapping("/users/{userId}/bookings/{bookingId}/confirm")
    public ResponseEntity<?> confirmBooking(@PathVariable String userId, @PathVariable String bookingId) {
        try {
            Bookings booking = bookingService.confirmBooking(bookingId);
            if (!booking.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            return ResponseEntity.ok(booking);
        } catch (IllegalStateException | NotFoundException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    // Cancel booking
    @PostMapping("/users/{userId}/bookings/{bookingId}/cancel")
    public ResponseEntity<?> cancelBooking(@PathVariable String userId, @PathVariable String bookingId) {
        try {
            Bookings booking = bookingService.getBookingById(bookingId);
            if (booking == null || !booking.getUserId().equals(userId)) {
                return ResponseEntity.notFound().build();
            }
            boolean cancelled = bookingService.cancelBooking(bookingId);
            return cancelled ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Check in
    @PostMapping("/users/{userId}/bookings/{bookingId}/checkin")
    public ResponseEntity<?> checkIn(@PathVariable String userId, @PathVariable String bookingId) {
        try {
            Bookings booking = bookingService.getBookingById(bookingId);
            if (booking == null || !booking.getUserId().equals(userId)) {
                return ResponseEntity.notFound().build();
            }
            booking = bookingService.checkIn(bookingId);
            return ResponseEntity.ok(booking);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Check out
    @PostMapping("/users/{userId}/bookings/{bookingId}/checkout")
    public ResponseEntity<?> checkOut(@PathVariable String userId, @PathVariable String bookingId) {
        try {
            Bookings booking = bookingService.getBookingById(bookingId);
            if (booking == null || !booking.getUserId().equals(userId)) {
                return ResponseEntity.notFound().build();
            }
            booking = bookingService.checkOut(bookingId);
            return ResponseEntity.ok(booking);
        } catch (InsufficientFundsException e) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body("Insufficient wallet coins");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // List all bookings for a user
    @GetMapping("/users/{userId}/bookings")
    public ResponseEntity<List<Bookings>> getUserBookings(@PathVariable String userId) {
        List<Bookings> bookings = bookingService.getBookingsByUserId(userId);
        // Always return 200 with empty list if no bookings, don't return 404
        return ResponseEntity.ok(bookings);
    }

    // Get a specific booking for a user
    @GetMapping("/users/{userId}/bookings/{bookingId}")
    public ResponseEntity<Bookings> getBookingById(@PathVariable String userId, @PathVariable String bookingId) {
        Bookings booking = bookingService.getBookingById(bookingId);
        if (booking == null || !booking.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(booking);
    }

    // Update booking status
    @PutMapping("/users/{userId}/bookings/{bookingId}")
    public ResponseEntity<?> updateBookingStatus(@PathVariable String userId,
                                                 @PathVariable String bookingId,
                                                 @RequestBody Bookings bookingDetails) {
        if (bookingDetails.getStatus() == null) {
            return ResponseEntity.badRequest().body("Status field is required");
        }
        try {
            Bookings existing = bookingService.getBookingById(bookingId);
            if (existing == null || !existing.getUserId().equals(userId)) {
                return ResponseEntity.notFound().build();
            }
            Bookings updated = bookingService.updateBookingStatus(bookingId, bookingDetails.getStatus());
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Delete booking
    @DeleteMapping("/users/{userId}/bookings/{bookingId}")
    public ResponseEntity<Void> deleteBooking(@PathVariable String userId, @PathVariable String bookingId) {
        Bookings booking = bookingService.getBookingById(bookingId);
        if (booking == null || !booking.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }
        bookingService.deleteBooking(bookingId);
        return ResponseEntity.noContent().build();
    }

//    // Get user's active booking
//    @GetMapping("/users/{userId}/bookings/active")
//    public ResponseEntity<Bookings> getActiveBooking(@PathVariable String userId) {
//        Bookings active = bookingService.getBookingHistoryByUserId(userId);
//        return active == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(active);
//    }

    // Get user's booking history
    @GetMapping("/users/{userId}/bookings/history")
    public ResponseEntity<List<Bookings>> getBookingHistory(@PathVariable String userId) {
        List<Bookings> history = bookingService.getBookingHistoryByUserId(userId);
        return history.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(history);
    }

    // Health check
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }
}
//}package com.parking.app.controller;
//
//import com.parking.app.exception.*;
//import com.parking.app.exception.IllegalStateException;
//import com.parking.app.model.Bookings;
//import com.parking.app.service.BookingService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.*;
//
//import java.text.ParseException;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequestMapping("/api/users/{userId}/bookings")
//public class BookingController {
//
//    private final BookingService bookingService;
//
//    private static final SimpleDateFormat ISO_DATE_TIME_FORMAT =
//            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX"); // ISO format with timezone
//
//    @Autowired
//    public BookingController(BookingService bookingService) {
//        this.bookingService = bookingService;
//    }
//
//    @PostMapping("/start")
//    public ResponseEntity<?> startBooking(
//            @PathVariable String userId,
//            @RequestParam String spotId,
//            @RequestParam String lotId,
//            @RequestParam String checkInTime,
//            @RequestParam String checkOutTime,
//            @RequestParam String vehicleNumber   // <- from frontend
//    ) {
//        try {
//            Date checkIn  = ISO_DATE_TIME_FORMAT.parse(checkInTime);
//            Date checkOut = ISO_DATE_TIME_FORMAT.parse(checkOutTime);
//
//            Bookings booking = bookingService.startBooking(
//                    spotId, userId, lotId, checkIn, checkOut, vehicleNumber
//            );
//            return ResponseEntity.ok(booking);
//        } catch (ConflictException e) {
//            return ResponseEntity.status(HttpStatus.CONFLICT).body("No spots available");
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body(e.getMessage());
//        }
//    }
//
//    // ... keep the rest of your existing controller methods unchanged ...
//
//    @PostMapping("/{bookingId}/confirm")
//    public ResponseEntity<?> confirmBooking(@PathVariable String userId, @PathVariable String bookingId) {
//        try {
//            Bookings booking = bookingService.confirmBooking(bookingId);
//            if (!booking.getUserId().equals(userId)) {
//                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//            }
//            return ResponseEntity.ok(booking);
//        } catch (IllegalStateException | NotFoundException e) {
//            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
//        }
//    }
//
//    @PostMapping("/{bookingId}/cancel")
//    public ResponseEntity<?> cancelBooking(@PathVariable String userId, @PathVariable String bookingId) {
//        try {
//            Bookings booking = bookingService.getBookingById(bookingId);
//            if (booking == null || !booking.getUserId().equals(userId)) {
//                return ResponseEntity.notFound().build();
//            }
//            boolean cancelled = bookingService.cancelBooking(bookingId);
//            return cancelled ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
//        } catch (IllegalStateException e) {
//            return ResponseEntity.badRequest().body(e.getMessage());
//        }
//    }
//
//    @PostMapping("/{bookingId}/checkin")
//    public ResponseEntity<?> checkIn(@PathVariable String userId, @PathVariable String bookingId) {
//        try {
//            Bookings booking = bookingService.getBookingById(bookingId);
//            if (booking == null || !booking.getUserId().equals(userId)) {
//                return ResponseEntity.notFound().build();
//            }
//            booking = bookingService.checkIn(bookingId);
//            return ResponseEntity.ok(booking);
//        } catch (IllegalStateException e) {
//            return ResponseEntity.badRequest().body(e.getMessage());
//        }
//    }
//
//    @PostMapping("/{bookingId}/checkout")
//    public ResponseEntity<?> checkOut(@PathVariable String userId, @PathVariable String bookingId) {
//        try {
//            Bookings booking = bookingService.getBookingById(bookingId);
//            if (booking == null || !booking.getUserId().equals(userId)) {
//                return ResponseEntity.notFound().build();
//            }
//            booking = bookingService.checkOut(bookingId);
//            return ResponseEntity.ok(booking);
//        } catch (InsufficientFundsException e) {
//            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body("Insufficient wallet coins");
//        } catch (IllegalStateException e) {
//            return ResponseEntity.badRequest().body(e.getMessage());
//        }
//    }
//
//    @GetMapping
//    public ResponseEntity<List<Bookings>> getAllBookings(@PathVariable String userId) {
//        List<Bookings> bookings = bookingService.getBookingsByUserId(userId);
//        return bookings.isEmpty() ? ResponseEntity.notFound().build() : ResponseEntity.ok(bookings);
//    }
//
//    @GetMapping("/{bookingId}")
//    public ResponseEntity<Bookings> getBookingById(@PathVariable String userId, @PathVariable String bookingId) {
//        Bookings booking = bookingService.getBookingById(bookingId);
//        if (booking == null || !booking.getUserId().equals(userId)) {
//            return ResponseEntity.notFound().build();
//        }
//        return ResponseEntity.ok(booking);
//    }
//
//    @PutMapping("/{bookingId}")
//    public ResponseEntity<?> updateBookingStatus(@PathVariable String userId,
//                                                 @PathVariable String bookingId,
//                                                 @RequestBody Bookings bookingDetails) {
//        if (bookingDetails.getStatus() == null) {
//            return ResponseEntity.badRequest().body("Status field is required");
//        }
//        try {
//            Bookings existing = bookingService.getBookingById(bookingId);
//            if (existing == null || !existing.getUserId().equals(userId)) {
//                return ResponseEntity.notFound().build();
//            }
//            Bookings updated = bookingService.updateBookingStatus(bookingId, bookingDetails.getStatus());
//            return ResponseEntity.ok(updated);
//        } catch (IllegalArgumentException e) {
//            return ResponseEntity.badRequest().body(e.getMessage());
//        }
//    }
//
//    @DeleteMapping("/{bookingId}")
//    public ResponseEntity<Void> deleteBooking(@PathVariable String userId, @PathVariable String bookingId) {
//        Bookings booking = bookingService.getBookingById(bookingId);
//        if (booking == null || !booking.getUserId().equals(userId)) {
//            return ResponseEntity.notFound().build();
//        }
//        bookingService.deleteBooking(bookingId);
//        return ResponseEntity.noContent().build();
//    }
//
//}
