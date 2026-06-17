package com.timearchive.adapter.outbound.storage

import com.timearchive.domain.port.MediaObjectStoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.Instant

@Component
class S3CompatibleMediaObjectStorageAdapter(
    private val s3Presigner: S3Presigner,
    @Value("\${time-archive.storage.s3.bucket}") private val bucket: String,
    @Value("\${time-archive.storage.s3.public-base-url}") private val publicBaseUrl: String,
) : MediaObjectStoragePort {
    override fun createPresignedUpload(command: MediaObjectStoragePort.Command): MediaObjectStoragePort.PresignedUpload {
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(command.objectKey)
            .contentType(command.contentType)
            .contentLength(command.contentLengthBytes)
            .build()

        val signatureDuration = Duration.between(Instant.now(), command.expiresAt)
        require(!signatureDuration.isNegative && !signatureDuration.isZero) {
            "upload expiration must be in the future"
        }

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(signatureDuration)
            .putObjectRequest(putObjectRequest)
            .build()

        val presignedRequest = s3Presigner.presignPutObject(presignRequest)

        return MediaObjectStoragePort.PresignedUpload(
            uploadUrl = presignedRequest.url().toString(),
            originalFileUrl = "${publicBaseUrl.trimEnd('/')}/${command.objectKey}",
            requiredHeaders = presignedRequest.signedHeaders()
                .mapValues { (_, values) -> values.joinToString(",") },
        )
    }
}
