package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CheckAvailability
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/archive")
class AvailabilityController(
    private val checkAvailability: CheckAvailability,
) {
    @GetMapping("/availability")
    fun checkAvailability(
        @RequestParam
        @Min(0)
        @Max(86_399)
        startSecond: Long,
        @RequestParam
        @Min(1)
        @Max(86_400)
        endSecond: Long,
    ): AvailabilityResponse {
        val result = checkAvailability.check(
            CheckAvailability.Query(
                startSecond = startSecond,
                endSecond = endSecond,
            ),
        )

        return AvailabilityResponse.from(result)
    }
}
