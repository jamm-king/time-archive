package com.timearchive.adapter.inbound.rest

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.validation.method.MethodValidationException
import org.springframework.validation.method.ParameterValidationResult
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val details = exception.bindingResult
            .fieldErrors
            .map { it.toApiErrorDetail() }
            .ifEmpty {
                listOf(ApiErrorDetail(field = null, message = "Request validation failed"))
            }

        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = "Request validation failed",
            details = details,
        )
    }

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MethodArgumentTypeMismatchException::class,
        MissingRequestHeaderException::class,
        MissingServletRequestParameterException::class,
    )
    fun handleInvalidRequest(): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = "Invalid request",
        )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = "Request validation failed",
            details = exception.constraintViolations.map {
                ApiErrorDetail(
                    field = it.propertyPath.lastOrNull()?.name,
                    message = it.message,
                )
            },
        )

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(exception: HandlerMethodValidationException): ResponseEntity<ApiErrorResponse> =
        methodValidationErrorResponse(exception.parameterValidationResults)

    @ExceptionHandler(MethodValidationException::class)
    fun handleMethodValidation(exception: MethodValidationException): ResponseEntity<ApiErrorResponse> =
        methodValidationErrorResponse(exception.parameterValidationResults)

    private fun methodValidationErrorResponse(
        parameterValidationResults: List<ParameterValidationResult>,
    ): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "INVALID_REQUEST",
            message = "Request validation failed",
            details = parameterValidationResults.flatMap { result ->
                result.resolvableErrors.map {
                    ApiErrorDetail(
                        field = result.methodParameter.parameterName,
                        message = it.defaultMessage ?: "Invalid value",
                    )
                }
            },
        )

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ApiErrorResponse> {
        val message = exception.message.orEmpty()
        return when {
            message.contains("time range already has active ownership") ->
                errorResponse(HttpStatus.CONFLICT, "TIME_RANGE_ALREADY_OWNED", "Time range already has active ownership")
            message.contains("time range already has active reservation") ->
                errorResponse(HttpStatus.CONFLICT, "TIME_RANGE_ALREADY_RESERVED", "Time range already has active reservation")
            message.contains("reservation is expired") ->
                errorResponse(HttpStatus.CONFLICT, "RESERVATION_EXPIRED", "Reservation is expired")
            message.contains("reservation is not held") || message.contains("reservation is not payable") ->
                errorResponse(HttpStatus.CONFLICT, "RESERVATION_NOT_PAYABLE", "Reservation is not payable")
            message.contains("payment event is already being processed") ->
                errorResponse(
                    HttpStatus.CONFLICT,
                    "PAYMENT_EVENT_ALREADY_PROCESSING",
                    "Payment event is already being processed",
                )
            message.contains("ownership record is not owned by current user") ->
                errorResponse(HttpStatus.FORBIDDEN, "OWNERSHIP_ACCESS_DENIED", "Ownership access denied")
            message.contains("ownership record is not active") ->
                errorResponse(HttpStatus.CONFLICT, "OWNERSHIP_NOT_ACTIVE", "Ownership record is not active")
            message.contains("media upload request is expired") ->
                errorResponse(HttpStatus.CONFLICT, "MEDIA_UPLOAD_REQUEST_EXPIRED", "Media upload request is expired")
            message.contains("media upload request is not completable") ->
                errorResponse(HttpStatus.CONFLICT, "MEDIA_UPLOAD_REQUEST_NOT_COMPLETABLE", "Media upload request is not completable")
            message.contains("uploaded media content length does not match upload request") ||
                message.contains("uploaded media content type does not match upload request") ->
                errorResponse(HttpStatus.CONFLICT, "MEDIA_UPLOAD_OBJECT_MISMATCH", "Uploaded media object does not match upload request")
            else ->
                errorResponse(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request")
        }
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(exception: IllegalStateException): ResponseEntity<ApiErrorResponse> {
        val message = exception.message.orEmpty()
        return when {
            message.contains("purchase reservation not found") ->
                errorResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Reservation was not found")
            message.contains("ownership record not found") ->
                errorResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Ownership record was not found")
            message.contains("media upload request not found") ->
                errorResponse(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Media upload request was not found")
            message.contains("uploaded media object not found") ->
                errorResponse(HttpStatus.CONFLICT, "MEDIA_UPLOAD_OBJECT_NOT_FOUND", "Uploaded media object was not found")
            message.contains("checkout status transition failed") ->
                errorResponse(HttpStatus.CONFLICT, "RESERVATION_NOT_PAYABLE", "Reservation is not payable")
            else ->
                errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "UNEXPECTED_ERROR", "Unexpected server error")
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(): ResponseEntity<ApiErrorResponse> =
        errorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "UNEXPECTED_ERROR",
            message = "Unexpected server error",
        )

    private fun FieldError.toApiErrorDetail(): ApiErrorDetail =
        ApiErrorDetail(
            field = field,
            message = defaultMessage ?: "Invalid value",
        )

    private fun errorResponse(
        status: HttpStatus,
        code: String,
        message: String,
        details: List<ApiErrorDetail> = emptyList(),
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity
            .status(status)
            .body(ApiErrorResponse(code = code, message = message, details = details))
}
