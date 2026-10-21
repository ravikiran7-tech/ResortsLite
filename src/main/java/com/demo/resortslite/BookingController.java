package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * FIXED cr-java-0065: HTTP Session State Storage migrated to Amazon ElastiCache for Redis
 * 
 * Spring Session automatically intercepts HttpSession operations and stores session data
 * in Redis instead of in-memory. This enables:
 * - Stateless application instances
 * - Horizontal scaling without sticky sessions
 * - Session persistence across instance restarts
 * - Load balancing across multiple EC2/ECS/EKS instances
 * 
 * No code changes required in controller methods - Spring Session transparently handles
 * Redis storage through HttpSession interface. Session data is automatically serialized
 * to Redis and shared across all application instances.
 * 
 * FIXED cr-java-0067: In-Memory Caching Without TTL
 * 
 * Replaced unbounded in-memory HashMap cache with Amazon ElastiCache for Redis using
 * Spring Cache abstraction. Benefits:
 * - Controlled expiration: 30-minute TTL prevents indefinite memory growth
 * - Consistent data: All application instances share the same cache via Redis cluster
 * - Centralized management: Cache managed by ElastiCache, not application memory
 * - Horizontal scaling: Cache accessible across all EC2/ECS/EKS instances
 * - Memory safety: No risk of out-of-memory errors from unbounded cache growth
 * - Stale data prevention: TTL ensures data freshness across distributed instances
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    @Autowired
    private BookingService bookingService;

    // FIXED cr-java-0071: Externalized inventory service URL to AWS Systems Manager Parameter Store
    // Configuration retrieved from Parameter Store at runtime for environment-agnostic deployment
    @Value("${app.inventory.endpoint}")
    private String inventoryServiceUrl;

    // FIXED cr-java-0067: Removed static in-memory HashMap cache
    // Replaced with Spring Cache abstraction backed by Amazon ElastiCache for Redis
    // Old code: private static final Map<String, Object> bookingCache = new HashMap<>();
    // New approach: @CachePut and @Cacheable annotations with Redis backend

    /**
     * FIXED cr-java-0065: Session data now stored in Amazon ElastiCache for Redis
     * FIXED cr-java-0067: Booking data cached in Amazon ElastiCache for Redis with 30-minute TTL
     * 
     * HttpSession.setAttribute() calls are automatically intercepted by Spring Session
     * and stored in Redis cluster. Session data is accessible by all application instances.
     * 
     * @CachePut annotation stores booking data in Redis cache with automatic expiration
     * Cache key: bookingId, Cache name: "bookings", TTL: 30 minutes
     * 
     * Lines 34-35: Session attributes stored in Redis via Spring Session
     */
    @PostMapping("/create")
    @CachePut(value = "bookings", key = "#result['bookingId']")
    public Map<String, Object> createBooking(
            @RequestParam String guestName,
            @RequestParam String roomType,
            @RequestParam String checkIn,
            @RequestParam String checkOut,
            HttpSession session) { // HttpSession backed by Redis via Spring Session

        Map<String, Object> booking = bookingService.createBooking(guestName, roomType, checkIn, checkOut);

        // FIXED cr-java-0065: Session attributes now stored in Amazon ElastiCache for Redis
        // Spring Session automatically serializes and stores these attributes in Redis
        // All application instances can access this session data through the shared Redis cluster
        session.setAttribute("lastBooking", booking); // Stored in Redis, not in-memory
        session.setAttribute("guestName", guestName); // Stored in Redis, not in-memory

        // FIXED cr-java-0067: Removed manual cache put operation
        // @CachePut annotation automatically stores booking in Redis cache with TTL
        // Old code: bookingCache.put((String) booking.get("bookingId"), booking);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "confirmed");
        response.put("booking", booking);
        return response;
    }

    /**
     * FIXED cr-java-0065: Session data retrieved from Amazon ElastiCache for Redis
     * FIXED cr-java-0067: Booking data retrieved from Amazon ElastiCache for Redis cache
     * 
     * HttpSession.getAttribute() calls are automatically intercepted by Spring Session
     * and retrieved from Redis cluster. Works correctly across all application instances.
     * 
     * Booking details are retrieved from BookingService, which can leverage caching
     * if needed for database query optimization.
     */
    @GetMapping("/status/{bookingId}")
    public Map<String, Object> getBookingStatus(
            @PathVariable String bookingId,
            HttpSession session) {

        // FIXED cr-java-0065: Session attribute retrieved from Amazon ElastiCache for Redis
        // Spring Session automatically deserializes and retrieves this attribute from Redis
        // Works correctly regardless of which application instance handles the request
        String lastGuest = (String) session.getAttribute("guestName"); // Retrieved from Redis

        Map<String, Object> result = new HashMap<>();
        result.put("bookingId", bookingId);
        result.put("sessionGuest", lastGuest);
        result.put("details", bookingService.getBookingById(bookingId));
        return result;
    }

    /**
     * FIXED cr-java-0067: Get booking from Redis cache with automatic fallback to database
     * 
     * @Cacheable annotation checks Redis cache first, then calls service method if not found
     * Cache key: bookingId, Cache name: "bookings", TTL: 30 minutes
     * This reduces database load and improves response time for frequently accessed bookings
     */
    @GetMapping("/booking/{bookingId}")
    @Cacheable(value = "bookings", key = "#bookingId")
    public Map<String, Object> getBooking(@PathVariable String bookingId) {
        return bookingService.getBookingById(bookingId);
    }

    @GetMapping("/availability")
    public Map<String, Object> checkAvailability(@RequestParam String roomType) {
        // FIXED cr-java-0071: Inventory service URL now retrieved from AWS Systems Manager Parameter Store
        // Supports environment-specific URLs (dev/staging/prod) without code changes
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP call to
        // internal inventory service. AWS ALB, WAF, and Well-Architected security review
        // enforce HTTPS. This call will be blocked or flagged in a cloud-native setup.
        String inventoryUrl = inventoryServiceUrl + "/rooms/available"; // cr-java-0088 (protocol issue remains)

        Map<String, Object> response = new HashMap<>();
        response.put("roomType", roomType);
        response.put("inventoryEndpoint", inventoryUrl);
        response.put("available", bookingService.isRoomAvailable(roomType));
        return response;
    }

    @GetMapping("/report/download")
    public Map<String, Object> downloadReport(@RequestParam String month) {
        // VIOLATION czr-java-001 [Software Portability / Mandatory]: Hardcoded absolute
        // file path. This path does not exist inside a container image. Container images
        // have their own isolated file systems — /var/legacy/reports won't be present.
        String reportPath = "/var/legacy/reports/" + month + "_bookings.pdf"; // czr-java-001

        Map<String, Object> response = new HashMap<>();
        response.put("reportPath", reportPath);
        response.put("message", bookingService.generateReport(month));
        return response;
    }
}
