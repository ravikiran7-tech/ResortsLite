package com.demo.resortslite.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * FIXED cr-java-0067: In-Memory Caching Without TTL
 * 
 * This configuration replaces unbounded in-memory caching (HashMap) with Amazon ElastiCache 
 * for Redis with proper TTL policies. This ensures:
 * 
 * - Controlled expiration: Cache entries automatically expire after TTL
 * - Consistent data: All application instances share the same cache via Redis cluster
 * - Centralized cache management: Cache is managed by ElastiCache, not application memory
 * - Horizontal scaling: Cache is accessible across all EC2/ECS/EKS instances
 * - Memory safety: No risk of out-of-memory errors from unbounded cache growth
 * - Stale data prevention: TTL ensures data freshness across distributed instances
 * 
 * AWS ElastiCache for Redis Benefits:
 * - Automatic failover and replication
 * - Backup and restore capabilities
 * - Monitoring via CloudWatch metrics
 * - Encryption at-rest and in-transit
 * - Multi-AZ deployment for high availability
 * 
 * Cache Configuration:
 * - Default TTL: 10 minutes (600 seconds)
 * - Booking cache TTL: 30 minutes (1800 seconds)
 * - Serialization: JSON for readability and debugging
 * - Key prefix: Namespace isolation for multiple applications
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Configure Redis-backed cache manager with TTL policies
     * 
     * @param connectionFactory Redis connection factory (auto-configured by Spring Boot)
     * @return CacheManager configured for Amazon ElastiCache for Redis
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration with 10-minute TTL
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )
                .disableCachingNullValues()
                .prefixCacheNameWith("resortslite:cache:");

        // Booking-specific cache configuration with 30-minute TTL
        RedisCacheConfiguration bookingCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )
                .disableCachingNullValues()
                .prefixCacheNameWith("resortslite:cache:");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("bookings", bookingCacheConfig)
                .transactionAware()
                .build();
    }
}
