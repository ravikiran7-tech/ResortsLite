package com.demo.resortslite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Amazon Cognito Authentication Service
 * Replaces file-based and weak hash-based authentication with AWS Cognito
 * for secure, scalable user identity management.
 * 
 * FIXED: cr-java-0090 - File-based Authentication
 * Migrated from MD5 hash-based confirmation codes to AWS Cognito for:
 * - Centralized user identity management
 * - Encrypted credential storage
 * - Built-in user lifecycle management
 * - Auditable authentication events
 * - Multi-factor authentication support
 */
@Service
public class CognitoAuthenticationService {

    @Value("${aws.cognito.userPoolId:}")
    private String userPoolId;

    @Value("${aws.cognito.clientId:}")
    private String clientId;

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    private CognitoIdentityProviderClient cognitoClient;
    private SecureRandom secureRandom;

    @PostConstruct
    public void init() {
        try {
            cognitoClient = CognitoIdentityProviderClient.builder()
                    .region(Region.of(awsRegion))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
            secureRandom = new SecureRandom();
        } catch (Exception e) {
            System.err.println("Warning: Unable to initialize Cognito client: " + e.getMessage());
            System.err.println("Authentication features will use fallback mode for local development");
        }
    }

    /**
     * Generate a secure confirmation code using SHA-256 instead of MD5
     * This replaces the weak MD5 hash with a cryptographically secure alternative
     * 
     * @param bookingId The booking identifier
     * @param guestName The guest name
     * @return A secure confirmation code
     */
    public String generateSecureConfirmationCode(String bookingId, String guestName) {
        try {
            // Use SHA-256 instead of MD5 for secure hashing
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            // Add salt for additional security
            byte[] salt = new byte[16];
            secureRandom.nextBytes(salt);
            
            String input = bookingId + guestName + Base64.getEncoder().encodeToString(salt);
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            
            // Return first 12 characters of Base64-encoded hash for user-friendly confirmation code
            String encoded = Base64.getEncoder().encodeToString(hash);
            return encoded.substring(0, Math.min(12, encoded.length())).toUpperCase();
        } catch (Exception e) {
            // Fallback to secure random if hashing fails
            byte[] randomBytes = new byte[9];
            secureRandom.nextBytes(randomBytes);
            return Base64.getEncoder().encodeToString(randomBytes).substring(0, 12).toUpperCase();
        }
    }

    /**
     * Create a user in Amazon Cognito User Pool
     * This enables centralized user management with AWS Cognito
     * 
     * @param username The username
     * @param email The user's email
     * @param temporaryPassword Temporary password for initial login
     * @return User creation result with user sub (unique identifier)
     */
    public Map<String, String> createCognitoUser(String username, String email, String temporaryPassword) {
        Map<String, String> result = new HashMap<>();
        
        if (cognitoClient == null || userPoolId == null || userPoolId.isEmpty()) {
            result.put("status", "fallback");
            result.put("message", "Cognito not configured - using local authentication");
            return result;
        }

        try {
            AttributeType emailAttribute = AttributeType.builder()
                    .name("email")
                    .value(email)
                    .build();

            AttributeType emailVerifiedAttribute = AttributeType.builder()
                    .name("email_verified")
                    .value("true")
                    .build();

            AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(username)
                    .temporaryPassword(temporaryPassword)
                    .userAttributes(emailAttribute, emailVerifiedAttribute)
                    .messageAction(MessageActionType.SUPPRESS) // Don't send welcome email
                    .build();

            AdminCreateUserResponse response = cognitoClient.adminCreateUser(createUserRequest);
            
            result.put("status", "success");
            result.put("username", response.user().username());
            result.put("userSub", response.user().attributes().stream()
                    .filter(attr -> "sub".equals(attr.name()))
                    .findFirst()
                    .map(AttributeType::value)
                    .orElse(""));
            
        } catch (UsernameExistsException e) {
            result.put("status", "error");
            result.put("message", "User already exists");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Failed to create user: " + e.getMessage());
        }

        return result;
    }

    /**
     * Authenticate a user with Amazon Cognito
     * 
     * @param username The username
     * @param password The password
     * @return Authentication result with tokens
     */
    public Map<String, String> authenticateUser(String username, String password) {
        Map<String, String> result = new HashMap<>();
        
        if (cognitoClient == null || userPoolId == null || userPoolId.isEmpty()) {
            result.put("status", "fallback");
            result.put("message", "Cognito not configured - using local authentication");
            return result;
        }

        try {
            Map<String, String> authParams = new HashMap<>();
            authParams.put("USERNAME", username);
            authParams.put("PASSWORD", password);

            AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
                    .userPoolId(userPoolId)
                    .clientId(clientId)
                    .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                    .authParameters(authParams)
                    .build();

            AdminInitiateAuthResponse authResponse = cognitoClient.adminInitiateAuth(authRequest);
            
            if (authResponse.authenticationResult() != null) {
                result.put("status", "success");
                result.put("accessToken", authResponse.authenticationResult().accessToken());
                result.put("idToken", authResponse.authenticationResult().idToken());
                result.put("refreshToken", authResponse.authenticationResult().refreshToken());
            } else {
                result.put("status", "challenge");
                result.put("challengeName", authResponse.challengeNameAsString());
                result.put("session", authResponse.session());
            }
            
        } catch (NotAuthorizedException e) {
            result.put("status", "error");
            result.put("message", "Invalid credentials");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Authentication failed: " + e.getMessage());
        }

        return result;
    }

    /**
     * Verify an access token from Cognito
     * 
     * @param accessToken The access token to verify
     * @return Verification result
     */
    public Map<String, String> verifyToken(String accessToken) {
        Map<String, String> result = new HashMap<>();
        
        if (cognitoClient == null) {
            result.put("status", "fallback");
            return result;
        }

        try {
            GetUserRequest getUserRequest = GetUserRequest.builder()
                    .accessToken(accessToken)
                    .build();

            GetUserResponse getUserResponse = cognitoClient.getUser(getUserRequest);
            
            result.put("status", "valid");
            result.put("username", getUserResponse.username());
            
        } catch (NotAuthorizedException e) {
            result.put("status", "invalid");
            result.put("message", "Token is invalid or expired");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", "Token verification failed: " + e.getMessage());
        }

        return result;
    }
}
