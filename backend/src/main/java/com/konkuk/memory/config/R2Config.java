package com.konkuk.memory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class R2Config {

    @Bean
    public S3Client r2Client(
            @Value("${R2_ACCESS_KEY_ID}") String accessKey,
            @Value("${R2_SECRET_ACCESS_KEY}") String secretKey,
            @Value("${R2_ENDPOINT}") String endpoint
    ) {
        AwsBasicCredentials credentials =
                AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(
                        StaticCredentialsProvider.create(credentials)
                )
                .region(Region.of("auto"))
                .build();
    }
}
