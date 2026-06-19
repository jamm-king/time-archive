package com.timearchive.adapter.outbound.storage

import com.timearchive.domain.port.MediaObjectStoragePort
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.time.Instant

@Component
class S3CompatibleMediaObjectStorageAdapter(
    private val s3Client: S3Client,
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

    override fun createPresignedDownload(
        command: MediaObjectStoragePort.DownloadCommand,
    ): MediaObjectStoragePort.PresignedDownload {
        val objectKey = objectKeyFromFileUrl(command.fileUrl)
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build()
        val signatureDuration = Duration.between(Instant.now(), command.expiresAt)
        require(!signatureDuration.isNegative && !signatureDuration.isZero) {
            "download expiration must be in the future"
        }
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(signatureDuration)
            .getObjectRequest(getObjectRequest)
            .build()
        val presignedRequest = s3Presigner.presignGetObject(presignRequest)

        return MediaObjectStoragePort.PresignedDownload(
            downloadUrl = presignedRequest.url().toString(),
            expiresAt = command.expiresAt,
        )
    }

    override fun findObjectMetadata(objectKey: String): MediaObjectStoragePort.ObjectMetadata? {
        val request = HeadObjectRequest.builder()
            .bucket(bucket)
            .key(objectKey)
            .build()

        return try {
            val response = s3Client.headObject(request)
            MediaObjectStoragePort.ObjectMetadata(
                objectKey = objectKey,
                contentType = response.contentType(),
                contentLengthBytes = response.contentLength(),
            )
        } catch (_: NoSuchKeyException) {
            null
        } catch (exception: S3Exception) {
            if (exception.statusCode() == 404) {
                null
            } else {
                throw exception
            }
        }
    }

    private fun objectKeyFromFileUrl(fileUrl: String): String {
        val normalizedBaseUrl = "${publicBaseUrl.trimEnd('/')}/"
        require(fileUrl.startsWith(normalizedBaseUrl)) {
            "media file url does not belong to configured storage"
        }
        val objectKey = fileUrl.removePrefix(normalizedBaseUrl).substringBefore("?")
        require(objectKey.isNotBlank()) { "media object key must not be blank" }
        return objectKey
    }
}
