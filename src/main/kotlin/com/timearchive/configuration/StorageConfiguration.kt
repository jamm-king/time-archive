package com.timearchive.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class StorageConfiguration {
    @Bean
    fun s3Presigner(
        @Value("\${time-archive.storage.s3.endpoint}") endpoint: String,
        @Value("\${time-archive.storage.s3.region}") region: String,
        @Value("\${time-archive.storage.s3.access-key}") accessKey: String,
        @Value("\${time-archive.storage.s3.secret-key}") secretKey: String,
        @Value("\${time-archive.storage.s3.path-style-access}") pathStyleAccess: Boolean,
    ): S3Presigner =
        S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey),
                ),
            )
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(pathStyleAccess)
                    .build(),
            )
            .build()
}
