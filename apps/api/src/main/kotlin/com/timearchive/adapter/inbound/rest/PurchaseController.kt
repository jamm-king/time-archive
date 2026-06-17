package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CreateCheckout
import com.timearchive.application.ReserveTimeRange
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/purchase")
class PurchaseController(
    private val reserveTimeRange: ReserveTimeRange,
    private val createCheckout: CreateCheckout,
) {
    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    fun createReservation(
        @Valid @RequestBody request: CreateReservationRequest,
    ): ReservationResponse {
        val reservation = reserveTimeRange.reserve(
            ReserveTimeRange.Command(
                buyerId = requireNotNull(request.buyerId),
                startSecond = requireNotNull(request.startSecond),
                endSecond = requireNotNull(request.endSecond),
            ),
        )

        return ReservationResponse.from(reservation)
    }

    @PostMapping("/reservations/{reservationId}/checkout")
    fun createCheckout(
        @PathVariable reservationId: UUID,
    ): CheckoutSessionResponse {
        val checkout = createCheckout.create(CreateCheckout.Command(reservationId = reservationId))
        return CheckoutSessionResponse.from(checkout)
    }
}
