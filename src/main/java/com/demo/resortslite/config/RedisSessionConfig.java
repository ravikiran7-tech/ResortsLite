package com.demo.resortslite.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * FIXED cr-java-0065: Spring Session with Amazon ElastiCache for Redis Configuration
 * 
 * This configuration enables distributed session management using Amazon ElastiCache for Redis,
 * replacing in-memory HTTP session storage with a centralized, distributed session store.
 * 
 * Benefits:
 * - Stateless application instances: Session data is stored externally in Redis
 * - Horizontal scaling: Multiple EC2/ECS/EKS instances can share session data
 * - High availability: ElastiCache provides automatic failover and replication
 * - Load balancing: No need for sticky sessions; any instance can handle any request
 * - Auto-scaling friendly: New instances immediately access existing session data
 * 
 * AWS ElastiCache for Redis Setup:
 * 1. Create ElastiCache Redis cluster in AWS Console or via CloudFormation/Terraform
 * 2. Configure cluster endpoint in application.properties or AWS Parameter Store
 * 3. Ensure security groups allow application instances to connect to Redis (port 6379)
 * 4. Enable encryption in-transit for production environments
 * 5. Use Redis cluster mode for high availability and automatic failover
 * 
 * Environment Variables:
 * - REDIS_HOST: ElastiCache cluster endpoint (e.g., resortslite-cache.abc123.0001.use1.cache.amazonaws.com)
 * - REDIS_PORT: Redis port (default: 6379)
 * - REDIS_PASSWORD: Redis AUTH password (if enabled)
 * - REDIS_SSL: Enable SSL/TLS for encryption in-transit (recommended for production)
 */
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800) // 30 minutes session timeout
public class RedisSessionConfig {

    /**
     * Configure RedisTemplate for session data serialization
     * Uses JSON serialization for better readability and debugging
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        
        // Use JSON serializer for values (session attributes)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        
        template.afterPropertiesSet();
        return template;
    }
}
