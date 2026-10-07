package com.subhrodip.squarewise.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.JwtValidationException
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Verifies reactive decoder construction fails before network discovery on incomplete config. */
class ReactiveOidcJwtDecoderFactoryTest {
    private val rsaJwk: RSAKey = RSAKeyGenerator(2048)
        .keyID("reactive-test-key")
        .generate()

    @Test
    fun `reactive decoder rejects blank issuer`() {
        assertThrows(PlatformDomainException::class.java) {
            ReactiveOidcJwtDecoderFactory.create("", "squarewise-api")
        }
    }

    @Test
    fun `reactive decoder rejects blank audience`() {
        assertThrows(PlatformDomainException::class.java) {
            ReactiveOidcJwtDecoderFactory.create("https://issuer.example", "")
        }
    }

    @Test
    fun `reactive issuer discovery factory builds a decoder from local metadata`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val issuer = "http://127.0.0.1:${server.address.port}"
        val metadata = """
            {"issuer":"$issuer","jwks_uri":"$issuer/jwks"}
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        server.createContext("/.well-known/openid-configuration") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, metadata.size.toLong())
            exchange.responseBody.use { it.write(metadata) }
        }
        server.start()

        try {
            assertNotNull(ReactiveOidcJwtDecoderFactory.create(issuer, "squarewise-api"))
        } finally {
            server.stop(0)
        }
    }

    /** The reactive path must apply the same signed-token trust policy as the servlet path. */
    @Test
    fun `reactive decoder validates signed token claims after issuer discovery`() {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val issuer = "http://127.0.0.1:${server.address.port}"
        val audience = "squarewise-api"
        val metadata = """
            {"issuer":"$issuer","jwks_uri":"$issuer/jwks"}
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        val jwks = "{\"keys\":[${rsaJwk.toPublicJWK().toJSONString()}]}"
            .toByteArray(StandardCharsets.UTF_8)
        server.createContext("/.well-known/openid-configuration") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, metadata.size.toLong())
            exchange.responseBody.use { it.write(metadata) }
        }
        server.createContext("/jwks") { exchange ->
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, jwks.size.toLong())
            exchange.responseBody.use { it.write(jwks) }
        }
        server.start()

        try {
            val decoder = ReactiveOidcJwtDecoderFactory.create(issuer, audience)
            val valid = mintToken(issuer, audience, "reactive-user")
            assertEquals("reactive-user", decoder.decode(valid).block()?.subject)

            val wrongAudience = mintToken(issuer, "other-api", "reactive-user")
            assertThrows(JwtValidationException::class.java) {
                decoder.decode(wrongAudience).block()
            }
        } finally {
            server.stop(0)
        }
    }

    private fun mintToken(issuer: String, audience: String, subject: String): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(Instant.now().plusSeconds(600)))
            .jwtID(UUID.randomUUID().toString())
            .build()
        val signedJwt = SignedJWT(
            JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.keyID).build(),
            claims
        )
        signedJwt.sign(RSASSASigner(rsaJwk.toRSAPrivateKey()))
        return signedJwt.serialize()
    }
}
