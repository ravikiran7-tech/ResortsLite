package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Provides the Amazon S3 client used for persistent report storage.
 * Region and credentials are resolved via the default AWS provider chains
 * (environment variables, IAM role / IRSA, instance profile, etc.).
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(new DefaultAwsRegionProviderChain().getRegion())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
