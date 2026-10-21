package com.demo.resortslite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class ReportService {

    // FIXED cr-java-0061: Replaced hard-coded file paths with S3 bucket configuration
    // Using environment variables and Spring configuration for cloud-native storage
    @Value("${aws.s3.bucket.name}")
    private String s3BucketName;

    @Value("${aws.s3.region}")
    private String awsRegion;

    @Value("${aws.s3.reports.prefix}")
    private String reportsPrefix;

    @Value("${aws.s3.backups.prefix}")
    private String backupsPrefix;

    // FIXED cr-java-0071: Externalized report download base URL to AWS Systems Manager Parameter Store
    // Configuration retrieved from Parameter Store at runtime for environment-agnostic deployment
    @Value("${app.reports.download.baseurl}")
    private String reportDownloadBaseUrl;

    // FIXED cr-java-0077: Replaced hard-coded port with environment variable injection from AWS Parameter Store
    // Port configuration now retrieved from AWS Systems Manager Parameter Store at runtime
    // Supports dynamic port assignment required by ECS, EKS, and Elastic Beanstalk
    @Value("${server.port}")
    private int serverPort; // FIXED cr-java-0077: Externalized to environment variable

    private S3Client s3Client;

    @PostConstruct
    public void initializeS3Client() {
        // Initialize AWS S3 client with default credentials provider
        // Uses IAM roles in AWS environments (EC2, ECS, Lambda)
        this.s3Client = S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @PreDestroy
    public void closeS3Client() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    public Map<String, Object> generateMonthlyReport(String month, String year) {
        String fileName = "resort_report_" + month + "_" + year + ".csv";
        // FIXED cr-java-0061: Using S3 key path instead of local file system path
        String s3Key = reportsPrefix + fileName;

        Map<String, Object> result = new HashMap<>();

        try {
            // Build CSV content in memory
            StringBuilder csvContent = new StringBuilder();
            csvContent.append("BookingID,GuestName,RoomType,CheckIn,CheckOut,Amount\n");
            csvContent.append("BK-001,John Smith,SUITE,2024-03-01,2024-03-05,1750.00\n");
            csvContent.append("BK-002,Jane Doe,DELUXE,2024-03-03,2024-03-07,960.00\n");

            // FIXED cr-java-0061: Upload to S3 instead of writing to local file system
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3BucketName)
                    .key(s3Key)
                    .contentType("text/csv")
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromString(csvContent.toString()));

            result.put("status", "generated");
            result.put("s3Bucket", s3BucketName);
            result.put("s3Key", s3Key);
            result.put("s3Uri", "s3://" + s3BucketName + "/" + s3Key);
            result.put("serverPort", serverPort); // FIXED cr-java-0077

        } catch (S3Exception e) {
            result.put("status", "error");
            result.put("message", "S3 upload failed: " + e.awsErrorDetails().errorMessage());
            result.put("errorCode", e.awsErrorDetails().errorCode());
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }

        return result;
    }

    // VIOLATION [Code Sustainability / Medium]: No JavaDoc or method documentation.
    // Missing documentation is flagged across all public methods in the codebase.
    // This increases onboarding time and transformation risk for automated tools.
    public String buildReportDownloadUrl(String reportName) { // doc-missing-001
        // FIXED cr-java-0071: Report download base URL now retrieved from AWS Systems Manager Parameter Store
        // Supports environment-specific URLs (dev/staging/prod) without code changes
        // VIOLATION cr-java-0088 [Cloud Compatibility / Mandatory]: Plain HTTP URL
        // hardcoded for report download. Cloud security standards enforce HTTPS.
        return reportDownloadBaseUrl + "/download/" + reportName; // cr-java-0088 (protocol issue remains)
    }

    public Map<String, Object> getSystemInfo() { // doc-missing-001
        // FIXED cr-java-0111: Migrated from java.util.Date/SimpleDateFormat to java.time API with UTC standardization
        // Eliminates timezone inconsistencies in distributed cloud environments
        String timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        Map<String, Object> info = new HashMap<>();
        // FIXED cr-java-0061: Return S3 configuration instead of local file paths
        info.put("s3BucketName", s3BucketName);
        info.put("s3Region", awsRegion);
        info.put("reportsPrefix", reportsPrefix);
        info.put("backupsPrefix", backupsPrefix);
        info.put("serverPort", serverPort);        // FIXED cr-java-0077
        info.put("generatedAt", timestamp);
        return info;
    }
}
