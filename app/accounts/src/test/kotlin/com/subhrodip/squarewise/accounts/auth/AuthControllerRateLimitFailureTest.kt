package com.subhrodip.squarewise.accounts.auth

import com.subhrodip.squarewise.errors.code.CategoryCode

import com.subhrodip.squarewise.accounts.auth.abuse.ClientAddressResolver
import com.subhrodip.squarewise.accounts.auth.abuse.RateLimitStoreUnavailableException
import com.subhrodip.squarewise.accounts.auth.abuse.RefreshRateLimitService
import com.subhrodip.squarewise.accounts.auth.credential.HmacCredentialDigest
import com.subhrodip.squarewise.accounts.auth.login.LoginStartService
import com.subhrodip.squarewise.accounts.auth.login.LoginVerificationService
import com.subhrodip.squarewise.accounts.auth.session.RefreshTokenRequest
import com.subhrodip.squarewise.accounts.auth.session.TokenSessionService
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException
import jakarta.servlet.http.HttpServletRequest
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.mockito.Mockito.mock
import com.subhrodip.squarewise.security.ratelimit.RateLimiter

/** Verifies refresh admission fails closed when the rate-limit store is unavailable. */
class AuthControllerRateLimitFailureTest {
    @Test
    fun `refresh token maps rate limit store outage to RATE_LIMITED`() {
        val digest = HmacCredentialDigest(ByteArray(32) { it.toByte() })
        val unavailableLimiter = object : RefreshRateLimitService(
            digest,
            mock(RateLimiter::class.java)
        ) {
            override fun tryAcquire(networkPartition: String, now: Instant): Boolean {
                throw RateLimitStoreUnavailableException(IllegalStateException("redis unavailable"))
            }
        }
        val controller = AuthController(
            loginStartService = mock(LoginStartService::class.java),
            loginVerificationService = mock(LoginVerificationService::class.java),
            tokenSessionService = mock(TokenSessionService::class.java),
            refreshRateLimitService = unavailableLimiter,
            clientAddressResolver = ClientAddressResolver()
        )
        val request: HttpServletRequest = MockHttpServletRequest().apply {
            remoteAddr = "127.0.0.1"
        }

        val error = assertThrows(SquarewiseException::class.java) {
            controller.refreshToken(RefreshTokenRequest("opaque-refresh-token"), request)
        }

        assertEquals(CategoryCode.RATE_LIMIT_EXCEEDED, error.definition.category)
        assertEquals("LOGIN_LIMITER_UNAVAILABLE", error.message)
    }
}
