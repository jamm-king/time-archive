package com.timearchive.adapter.inbound.rest

import com.timearchive.application.CapturePayPalOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class PayPalPaymentControllerTest {
    private val capturePayPalOrder: CapturePayPalOrder = mockk()
    private val currentUserSession = CurrentUserSession()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(PayPalPaymentController(capturePayPalOrder, currentUserSession))
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `captures paypal order for current user`() {
        val userId = UUID.randomUUID()
        every { capturePayPalOrder.capture(any()) } returns CapturePayPalOrder.Result(
            orderId = "paypal-order-1",
            captureReference = "paypal-capture-1",
            status = "CAPTURED_PENDING_WEBHOOK",
            alreadyCaptured = false,
        )

        mockMvc.post("/api/payments/paypal/orders/{orderId}/capture", "paypal-order-1") {
            session = signedInSession(userId)
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.orderId") { value("paypal-order-1") }
                jsonPath("$.captureReference") { value("paypal-capture-1") }
                jsonPath("$.status") { value("CAPTURED_PENDING_WEBHOOK") }
                jsonPath("$.alreadyCaptured") { value(false) }
            }

        verify {
            capturePayPalOrder.capture(
                CapturePayPalOrder.Command(
                    currentUserId = userId,
                    orderId = "paypal-order-1",
                ),
            )
        }
    }

    @Test
    fun `requires authentication`() {
        mockMvc.post("/api/payments/paypal/orders/{orderId}/capture", "paypal-order-1")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }
    }

    private fun signedInSession(userId: UUID): MockHttpSession =
        MockHttpSession().apply {
            setAttribute(CurrentUserSession.CURRENT_USER_ID_SESSION_ATTRIBUTE, userId.toString())
        }
}
