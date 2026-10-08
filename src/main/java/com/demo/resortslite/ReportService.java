package com.demo.resortslite;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
// Updated from java.util.Date / java.text.SimpleDateFormat to java.time API
// (JAVA8_TO_25_DATE_TIME_CHANGES): legacy date/time APIs are discouraged on Java 25
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
// Explicit charset import for FileWriter (JAVA8_TO_25_UTF8_DEFAULT_CHARSET)
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // NOTE: Hardcoded absolute path. /var/legacy/reports does not exist in a Docker
    // container image. Use volume mounts, cloud object storage (S3/Azure Blob), or
    // an environment variable for the report base path.
    private static final String REPORT_BASE_PATH = "/var/legacy/reports/";

    // NOTE: Windows-style absolute path will fail on Linux-based containers or cloud hosts.
    // Replace with a platform-neutral path or environment variable.
    private static final String BACKUP_PATH = "C:\\ResortBackups\\nightly\\";

    // NOTE: Fixed server port hardcoded in application logic.
    // Container orchestration (ECS/EKS) dynamically assigns ports.
    // Use server.port property or environment variable instead.
    private static final int SERVER_PORT = 8080;

    /**
     * Generates a monthly booking report CSV file.
     *
     * @param month the month identifier (e.g. "03")
     * @param year  the four-digit year (e.g. "2024")
     * @return a map containing the generation status and file path
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = REPORT_BASE_PATH + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(REPORT_BASE_PATH);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }

            // Explicitly specify UTF-8 charset on FileWriter to comply with
            // JAVA8_TO_25_UTF8_DEFAULT_CHARSET: Java 18+ uses UTF-8 as the default
            // charset, so naming it explicitly avoids platform-encoding surprises.
            // Using try-with-resources to prevent resource leak (FileWriter is AutoCloseable).
            try (FileWriter writer = new FileWriter(fullPath, StandardCharsets.UTF_8)) {
                writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
                writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
                writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            }

            result.put("status", "generated");
            result.put("path", fullPath);
            result.put("serverPort", SERVER_PORT);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds the download URL for a named report file.
     *
     * @param reportName the report file name
     * @return the full download URL string
     */
    public String buildReportDownloadUrl(String reportName) {
        // NOTE: Plain HTTP URL hardcoded for report download.
        // Cloud security standards enforce HTTPS. Update to HTTPS for production.
        return "http://reports.resorts-internal.com:8080/download/" + reportName;
    }

    /**
     * Returns system information including configured paths and the current timestamp.
     *
     * @return a map of system information key-value pairs
     */
    public Map<String, Object> getSystemInfo() {
        // Updated from java.util.Date + SimpleDateFormat to java.time.LocalDateTime
        // (JAVA8_TO_25_DATE_TIME_CHANGES): java.util.Date and SimpleDateFormat are
        // legacy APIs; java.time is the standard date/time library from Java 8 onward
        // and is the recommended approach on Java 25.
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", REPORT_BASE_PATH);
        info.put("backupPath", BACKUP_PATH);
        info.put("serverPort", SERVER_PORT);
        info.put("generatedAt", timestamp);
        return info;
    }
}
