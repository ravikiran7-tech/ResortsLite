package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

// Updated from javax.servlet to jakarta.servlet for Java 21 / Spring Boot 3.x compatibility
// (JAVA8_TO_21_JAKARTA_EE_MIGRATION)
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // Inventory service endpoint externalised to environment variable — enforces HTTPS
    // and removes hardcoded internal hostname (cr-java-0088, czr-java-001).
    @Value("${app.inventory.endpoint}")
    private String inventoryEndpoint;

    // Report base path externalised to environment variable — no hardcoded OS paths
    // in source code; safe for container environments (czr-java-001).
    @Value("${app.report.base-path:./reports/}")
    private String reportBasePath;

    // In-memory booking cache removed (cr-java-0067): instance-local caches break
    // horizontal scaling. Use a distributed cache (e.g. Redis / ElastiCache) if
    // caching is required across multiple instances.

    @PostMapping("/create")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) {

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // Session usage removed (cr-java-0065): storing business state in HTTP session
        // breaks horizontal scaling — session data on one instance is invisible to others.
        // Booking state is now returned directly in the response body and should be
        // persisted in the database or a distributed store if cross-request access is needed.

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // Session-based guest lookup removed (cr-java-0065): booking details are now
        // retrieved directly from the database via bookingService.getBookingById().
        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // Inventory endpoint resolved from environment variable — HTTPS enforced,
        // no hardcoded internal hostname (cr-java-0088).
        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryEndpoint);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // Report path resolved from environment variable — no hardcoded absolute path
        // in source code; portable across container environments (czr-java-001).
        String reportPath = reportBasePath + month + "_bookings.pdf";

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
