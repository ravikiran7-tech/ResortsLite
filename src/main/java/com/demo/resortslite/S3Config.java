package com.demo.resortslite;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Provides an Amazon S3 client. Credentials and region are resolved from the default
 * AWS provider chains (IAM role / environment variables / AWS_REGION).
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(new DefaultAwsRegionProviderChain().getRegion())
                .build();
    }
}
