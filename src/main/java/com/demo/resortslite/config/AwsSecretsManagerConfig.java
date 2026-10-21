package com.demo.resortslite.config;

import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * AWS Secrets Manager Configuration
 * Retrieves database credentials and other sensitive configuration from AWS Secrets Manager
 * instead of hard-coding them in source code or property files.
 */
@Configuration
public class AwsSecretsManagerConfig {

    @Value("${aws.secretsmanager.secret.name:resortslite/database/credentials}")
    private String secretName;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    private Map<String, String> secrets = new HashMap<>();

    @PostConstruct
    public void init() {
        loadSecretsFromAwsSecretsManager();
    }

    /**
     * Retrieves secrets from AWS Secrets Manager
     * Expected secret format (JSON):
     * {
     *   "db.host": "db-prod.resorts-internal.com",
     *   "db.username": "admin",
     *   "db.password": "Resort$Pass#2019!",
     *   "payment.api.url": "http://10.0.1.45:9090/payments/charge"
     * }
     */
    private void loadSecretsFromAwsSecretsManager() {
        try {
            SecretsManagerClient client = SecretsManagerClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();

            GetSecretValueRequest getSecretValueRequest = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse getSecretValueResponse = client.getSecretValue(getSecretValueRequest);
            String secretString = getSecretValueResponse.secretString();

            // Parse JSON secret
            Gson gson = new Gson();
            Map<String, String> secretMap = gson.fromJson(secretString, Map.class);
            secrets.putAll(secretMap);

            client.close();
        } catch (Exception e) {
            // Fallback to environment variables if Secrets Manager is not available
            // This allows local development without AWS credentials
            System.err.println("Warning: Unable to retrieve secrets from AWS Secrets Manager: " + e.getMessage());
            System.err.println("Falling back to environment variables for local development");
            loadSecretsFromEnvironment();
        }
    }

    /**
     * Fallback method to load secrets from environment variables
     * Used for local development when AWS Secrets Manager is not available
     */
    private void loadSecretsFromEnvironment() {
        secrets.put("db.host", System.getenv().getOrDefault("DB_HOST", "localhost"));
        secrets.put("db.username", System.getenv().getOrDefault("DB_USERNAME", "sa"));
        secrets.put("db.password", System.getenv().getOrDefault("DB_PASSWORD", ""));
        secrets.put("payment.api.url", System.getenv().getOrDefault("PAYMENT_API_URL", "http://localhost:9090/payments/charge"));
    }

    public String getSecret(String key) {
        return secrets.getOrDefault(key, "");
    }

    public String getDbHost() {
        return getSecret("db.host");
    }

    public String getDbUsername() {
        return getSecret("db.username");
    }

    public String getDbPassword() {
        return getSecret("db.password");
    }

    public String getPaymentApiUrl() {
        return getSecret("payment.api.url");
    }

    @Bean
    public SecretsManagerClient secretsManagerClient() {
        return SecretsManagerClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
