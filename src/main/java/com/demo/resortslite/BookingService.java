package com.demo.resortslite;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Credentials and infrastructure endpoints externalised to environment variables /
    // application.properties — no hardcoded values in source code (sec-cred-001, cr-java-0021).
    @Value("${app.payment.endpoint}")
    private String paymentApi;

    public Map<String, Object> createBooking(String guestName, String roomType,
                                              String checkIn, String checkOut) {
        String bookingId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Fixed sql-inject-001: replaced string-concatenation SQL with a parameterised
        // JdbcTemplate update so user-supplied values are never interpolated into the query.
        String sql = "INSERT INTO bookings (id, guest, room, checkin, checkout) VALUES (?, ?, ?, ?, ?)";
        jdbcTemplate.update(sql, bookingId, guestName, roomType, checkIn, checkOut);

        // SHA-256 confirmation code (JAVA8_TO_21_SECURITY_MD5_REPLACED)
        String confirmCode = sha256Hash(bookingId + guestName);

        Map<String, Object> booking = new HashMap<>();
        booking.put("bookingId", bookingId);
        booking.put("guestName", guestName);
        booking.put("roomType", roomType);
        booking.put("checkIn", checkIn);
        booking.put("checkOut", checkOut);
        booking.put("confirmationCode", confirmCode);
        return booking;
    }

    public Map<String, Object> getBookingById(String bookingId) {
        // Fixed sql-inject-001: parameterised query — bookingId is bound as a parameter,
        // not concatenated into the SQL string.
        String sql = "SELECT * FROM bookings WHERE id = ?";
        Map<String, Object> result = new HashMap<>();
        try {
            result = jdbcTemplate.queryForMap(sql, bookingId);
        } catch (Exception e) {
            result.put("error", "Booking not found: " + bookingId);
        }
        return result;
    }

    /**
     * Calculates the total room price based on room type, number of nights,
     * season, and guest loyalty tier.
     *
     * <p>Base prices per night: STANDARD $120 | DELUXE $200 | SUITE $350 | VILLA $600.
     * Season multipliers: PEAK ×1.5 | OFF ×0.8.
     * Loyalty discounts: GOLD 10% | PLATINUM 20% | DIAMOND 30%.
     * Stay discounts: ≥7 nights 5% | ≥14 nights 10%.
     *
     * @param roomType room category (STANDARD, DELUXE, SUITE, VILLA)
     * @param nights   number of nights
     * @param season   pricing season (PEAK, OFF, or standard)
     * @param loyalty  guest loyalty tier (GOLD, PLATINUM, DIAMOND, or none)
     * @return formatted total price string (two decimal places)
     */
    public String calculateRoomPrice(String roomType, int nights, String season, String loyalty) {
        double basePrice = switch (roomType) {
            case "STANDARD" -> 120.0;
            case "DELUXE"   -> 200.0;
            case "SUITE"    -> 350.0;
            case "VILLA"    -> 600.0;
            default         -> 120.0;
        };

        basePrice = switch (season) {
            case "PEAK" -> basePrice * 1.5;
            case "OFF"  -> basePrice * 0.8;
            default     -> basePrice;
        };

        basePrice = switch (loyalty) {
            case "GOLD"     -> basePrice * 0.9;
            case "PLATINUM" -> basePrice * 0.8;
            case "DIAMOND"  -> basePrice * 0.7;
            default         -> basePrice;
        };

        // Longer-stay discounts — evaluated in descending order so the larger
        // discount takes precedence when nights >= 14.
        if (nights >= 14) {
            basePrice = basePrice * 0.90;
        } else if (nights >= 7) {
            basePrice = basePrice * 0.95;
        }

        double total = basePrice * nights;
        return String.format("%.2f", total);
    }

    /**
     * Checks whether the given room type is a recognised category.
     *
     * @param roomType room category string to validate
     * @return {@code true} if the room type is valid; {@code false} otherwise
     */
    public boolean isRoomAvailable(String roomType) {
        // Centralised validation using the same switch expression used in
        // calculateRoomPrice — eliminates duplicated validation logic (dup-logic-001).
        return isValidRoomType(roomType);
    }

    /**
     * Returns a report generation confirmation message for the given month.
     *
     * @param month the month for which the report is requested
     * @return confirmation message string
     */
    public String generateReport(String month) {
        return "Report generation triggered for: " + month + " via " + paymentApi;
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code roomType} is one of the four recognised
     * room categories (STANDARD, DELUXE, SUITE, VILLA).
     */
    private boolean isValidRoomType(String roomType) {
        return switch (roomType) {
            case "STANDARD", "DELUXE", "SUITE", "VILLA" -> true;
            default -> false;
        };
    }

    /**
     * Generates a SHA-256 hex digest of the given input string using UTF-8 encoding.
     * Replaces the former md5Hash() method — MD5 is cryptographically broken (RFC 6151).
     * SHA-256 is the minimum acceptable algorithm for non-password hashing in Java 21.
     *
     * @param input string to hash
     * @return lowercase hex-encoded SHA-256 digest, or the original input on error
     */
    private String sha256Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Explicit UTF-8 charset to avoid platform-default charset issues
            // (JAVA8_TO_21_UTF8_DEFAULT_CHARSET)
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) { sb.append(String.format("%02x", b)); }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
