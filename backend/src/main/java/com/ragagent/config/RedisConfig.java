package com.ragagent.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ClientSideConfig;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Redis is shared with the Go scheduler's Asynq queue (same host:port in docker-compose).
 * This config wires the two clients the backend needs on top of it:
 *  - Lettuce, for distributed Bucket4j rate-limit buckets (com.ragagent.filter.RateLimitFilter)
 *  - Redisson, for the cluster-wide sandbox concurrency semaphore (com.ragagent.sandbox.SandboxService)
 *
 * Plain key/value Redis access (sandbox container tracking, fallback answer cache)
 * uses Spring Boot's auto-configured {@code StringRedisTemplate}, which reuses the
 * same spring.data.redis.host/port settings.
 */
@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    public RedisClient redisClient(@Value("${spring.data.redis.host}") String host,
                                    @Value("${spring.data.redis.port}") int port) {
        return RedisClient.create(RedisURI.Builder.redis(host, port).build());
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(@Value("${spring.data.redis.host}") String host,
                                          @Value("${spring.data.redis.port}") int port) {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + host + ":" + port);
        return Redisson.create(config);
    }

    /** Distributed Bucket4j buckets — same key per user/IP+category resolves to the same bucket across all backend instances. */
    @Bean
    public ProxyManager<byte[]> bucketProxyManager(RedisClient redisClient) {
        return LettuceBasedProxyManager.builderFor(redisClient)
                .withClientSideConfig(ClientSideConfig.getDefault()
                        .withExpirationAfterWriteStrategy(
                                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofSeconds(10))))
                .build();
    }
}
