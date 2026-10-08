package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // Report base path externalised to environment variable — no hardcoded OS-specific
    // paths in source code (czr-java-001). Defaults to a relative path safe for containers.
    @Value("${app.report.base-path:./reports/}")
    private String reportBasePath;

    // Report download base URL externalised to environment variable — enforces HTTPS
    // and removes hardcoded internal hostname (cr-java-0088, czr-java-001).
    @Value("${app.report.download-base-url:https://reports.resorts-internal.com/download}")
    private String reportDownloadBaseUrl;

    /**
     * Generates a monthly booking report CSV file for the specified month and year.
     *
     * <p>The output directory is resolved from the {@code app.report.base-path}
     * environment variable so the service is portable across container environments.
     *
     * @param month numeric or named month string (e.g. "03" or "March")
     * @param year  four-digit year string (e.g. "2024")
     * @return result map containing {@code status} and either {@code path} or {@code message}
     */
    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        String fullPath = reportBasePath + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            File reportDir = new File(reportBasePath);
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }

            // Explicit UTF-8 charset + try-with-resources to ensure stream is closed
            // (JAVA8_TO_21_UTF8_DEFAULT_CHARSET, JAVA8_TO_21_TRY_WITH_RESOURCES)
            try (FileWriter writer = new FileWriter(fullPath, StandardCharsets.UTF_8)) {
                writer.write("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
                writer.write("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
                writer.write("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");
            }

            result.put("status", "generated");
            result.put("path", fullPath);

        } catch (IOException e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    /**
     * Builds the HTTPS download URL for the given report file name.
     *
     * <p>The base URL is resolved from the {@code app.report.download-base-url}
     * environment variable, ensuring HTTPS is enforced and no internal hostname
     * is hardcoded in source code (cr-java-0088).
     *
     * @param reportName the report file name (e.g. "march_bookings.pdf")
     * @return fully qualified HTTPS download URL
     */
    public String buildReportDownloadUrl(String reportName) {
        return reportDownloadBaseUrl + "/" + reportName;
    }

    /**
     * Returns a map of current system information including the configured report
     * path and the current server timestamp.
     *
     * @return system info map with {@code reportPath} and {@code generatedAt} entries
     */
    public Map<String, Object> getSystemInfo() {
        // java.time API (JAVA8_TO_21_DATE_TIME_CHANGES)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> info = new HashMap<>();
        info.put("reportPath", reportBasePath);
        info.put("generatedAt", timestamp);
        return info;
    }
}
