package com.agentsystem.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Connection details for the Garage (S3-compatible) cluster.
 *
 * Bound from:
 *   garage.endpoint    (env: GARAGE_S3_ENDPOINT)
 *   garage.region      (env: GARAGE_REGION)
 *   garage.access-key  (env: GARAGE_ACCESS_KEY)
 *   garage.secret-key  (env: GARAGE_SECRET_KEY)
 *   garage.bucket      (env: GARAGE_BUCKET)
 */
@ConfigurationProperties(prefix = "garage")
public record GarageProperties(String endpoint, String region, String accessKey, String secretKey, String bucket) {}
