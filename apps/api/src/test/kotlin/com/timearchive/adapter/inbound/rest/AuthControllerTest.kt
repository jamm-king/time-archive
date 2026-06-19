package com.timearchive.adapter.inbound.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.timearchive.application.AuthenticateUser
import com.timearchive.application.GetCurrentUser
import com.timearchive.application.RegisterUser
import com.timearchive.domain.model.UserAccount
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class AuthControllerTest {
    private val registerUser: RegisterUser = mockk()
    private val authenticateUser: AuthenticateUser = mockk()
    private val getCurrentUser: GetCurrentUser = mockk()
    private val currentUserSession = CurrentUserSession()
    private val objectMapper: ObjectMapper = jacksonObjectMapper().findAndRegisterModules()
    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                AuthController(
                    registerUser = registerUser,
                    authenticateUser = authenticateUser,
                    getCurrentUser = getCurrentUser,
                    currentUserSession = currentUserSession,
                ),
            )
            .setControllerAdvice(ApiExceptionHandler())
            .build()
    }

    @Test
    fun `registers user and stores session identity`() {
        val user = userAccount()
        val session = MockHttpSession()
        every { registerUser.register(any()) } returns user

        mockMvc.post("/api/auth/register") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "user@example.com",
                    "password" to "password123",
                    "displayName" to "User",
                ),
            )
            this.session = session
        }
            .andExpect {
                status { isCreated() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.email") { value("user@example.com") }
                jsonPath("$.displayName") { value("User") }
                jsonPath("$.role") { value("USER") }
            }

        verify {
            registerUser.register(
                RegisterUser.Command(
                    email = "user@example.com",
                    password = "password123",
                    displayName = "User",
                ),
            )
        }
    }

    @Test
    fun `logs in user and stores session identity`() {
        val user = userAccount()
        val session = MockHttpSession()
        every { authenticateUser.authenticate(any()) } returns user

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "user@example.com",
                    "password" to "password123",
                ),
            )
            this.session = session
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.role") { value("USER") }
            }

        verify {
            authenticateUser.authenticate(
                AuthenticateUser.Command(
                    email = "user@example.com",
                    password = "password123",
                ),
            )
        }
    }

    @Test
    fun `maps invalid credentials to unauthorized`() {
        every { authenticateUser.authenticate(any()) } throws IllegalArgumentException("invalid credentials")

        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                mapOf(
                    "email" to "user@example.com",
                    "password" to "password123",
                ),
            )
        }
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("INVALID_CREDENTIALS") }
            }
    }

    @Test
    fun `returns current user from session`() {
        val user = userAccount()
        val session = MockHttpSession()
        currentUserSession.signIn(session, user.id)
        every { getCurrentUser.get(any()) } returns user

        mockMvc.get("/api/me") {
            this.session = session
        }
            .andExpect {
                status { isOk() }
                jsonPath("$.userId") { value(user.id.toString()) }
                jsonPath("$.email") { value("user@example.com") }
                jsonPath("$.role") { value("USER") }
            }

        verify { getCurrentUser.get(GetCurrentUser.Query(userId = user.id)) }
    }

    @Test
    fun `rejects current user request without session`() {
        mockMvc.get("/api/me")
            .andExpect {
                status { isUnauthorized() }
                jsonPath("$.code") { value("AUTHENTICATION_REQUIRED") }
            }
    }

    @Test
    fun `logs out by invalidating session`() {
        val session = MockHttpSession()

        mockMvc.post("/api/auth/logout") {
            this.session = session
        }
            .andExpect {
                status { isNoContent() }
            }
    }

    private fun userAccount(): UserAccount =
        UserAccount.create(
            id = UUID.randomUUID(),
            email = "user@example.com",
            passwordHash = "hashed",
            displayName = "User",
            now = Instant.parse("2026-06-18T00:00:00Z"),
        )
}
