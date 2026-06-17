package com.timearchive.adapter.inbound.rest

import com.timearchive.application.ListPublicTimelineSegments
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/api/timeline")
class PublicTimelineController(
    private val listPublicTimelineSegments: ListPublicTimelineSegments,
) {
    @GetMapping
    fun list(
        @RequestParam(name = "from")
        @Min(0)
        @Max(86_399)
        from: Long,
        @RequestParam(name = "to")
        @Min(1)
        @Max(86_400)
        to: Long,
    ): PublicTimelineResponse =
        PublicTimelineResponse.from(
            listPublicTimelineSegments.list(
                ListPublicTimelineSegments.Query(
                    from = from,
                    to = to,
                ),
            ),
        )
}
