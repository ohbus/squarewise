package com.subhrodip.squarewise.security

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.interfaces.RSAPublicKey
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Date
import java.util.UUID
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.BadJwtException
import com.nimbusds.jose.proc.SecurityContext
import com.subhrodip.squarewise.security.errors.PlatformDomainException

/** Verifies the key-based and JWK-source based OidcJwtDecoderFactory methods. */
class OidcJwtDecoderFactoryTest {

    private val rsaJwk: RSAKey = RSAKeyGenerator(2048)
        .keyID("test-key-1")
        .generate()
    private val publicKey: RSAPublicKey = rsaJwk.toRSAPublicKey()
    private val issuerUri: String = "https://accounts.squarewise.io"
    private val audience: String = "squarewise-api"

    @Test
    fun `createWithPublicKey validates valid token correctly`() {
        val decoder = OidcJwtDecoderFactory.createWithPublicKey(publicKey, issuerUri, audience)
        val token = mintToken(issuer = issuerUri, audience = audience, subject = "usr-123")

        val jwt = decoder.decode(token)
        assertNotNull(jwt)
        assertEquals(issuerUri, jwt.issuer.toString())
        assertEquals("usr-123", jwt.subject)
    }

    @Test
    fun `createWithPublicKey rejects token with wrong audience`() {
        val decoder = OidcJwtDecoderFactory.createWithPublicKey(publicKey, issuerUri, audience)
        val token = mintToken(issuer = issuerUri, audience = "wrong-aud", subject = "usr-123")

        assertThrows(BadJwtException::class.java) {
            decoder.decode(token)
        }
    }

    @Test
    fun `createWithPublicKey rejects token with wrong issuer`() {
        val decoder = OidcJwtDecoderFactory.createWithPublicKey(publicKey, issuerUri, audience)
        val token = mintToken(issuer = "https://wrong-issuer.com", audience = audience, subject = "usr-123")

        assertThrows(BadJwtException::class.java) {
            decoder.decode(token)
        }
    }

    @Test
    fun `createWithPublicKey rejects expired token`() {
        val decoder = OidcJwtDecoderFactory.createWithPublicKey(publicKey, issuerUri, audience)
        val token = mintToken(
            issuer = issuerUri,
            audience = audience,
            subject = "usr-123",
            expiresAt = Instant.now().minusSeconds(300)
        )

        assertThrows(BadJwtException::class.java) {
            decoder.decode(token)
        }
    }

    @Test
    fun `createWithJwkSource resolves keys and decodes successfully`() {
        val jwkSource = ImmutableJWKSet<SecurityContext>(JWKSet(rsaJwk))
        val decoder = OidcJwtDecoderFactory.createWithJwkSource(jwkSource, issuerUri, audience)
        val token = mintToken(issuer = issuerUri, audience = audience, subject = "usr-456")

        val jwt = decoder.decode(token)
        assertNotNull(jwt)
        assertEquals("usr-456", jwt.subject)
    }

    @Test
    fun `issuer discovery factory builds a decoder from local metadata`() {
        withDiscoveryServer { issuer ->
            val decoder = OidcJwtDecoderFactory.create(issuer, audience)
            val token = mintToken(issuer = issuer, audience = audience, subject = "discovered-user")

            assertEquals("discovered-user", decoder.decode(token).subject)
        }
    }

    /** Every direct decoder factory rejects incomplete trust-boundary configuration. */
    @Test
    fun `decoder factories reject blank issuer or audience`() {
        assertThrows(PlatformDomainException::class.java) {
            OidcJwtDecoderFactory.create("", audience)
        }
        assertThrows(PlatformDomainException::class.java) {
            OidcJwtDecoderFactory.create(issuerUri, "")
        }
        assertThrows(PlatformDomainException::class.java) {
            OidcJwtDecoderFactory.createWithPublicKey(publicKey, "", audience)
        }
        assertThrows(PlatformDomainException::class.java) {
            OidcJwtDecoderFactory.createWithPublicKey(publicKey, issuerUri, "")
        }
        val jwkSource = ImmutableJWKSet<SecurityContext>(JWKSet(rsaJwk))
        assertThrows(PlatformDomainException::class.java) {
            OidcJwtDecoderFactory.createWithJwkSource(jwkSource, "", audience)
        }
        assertThrows(PlatformDomainException::class.java) {
            OidcJwtDecoderFactory.createWithJwkSource(jwkSource, issuerUri, "")
        }
    }

    private fun mintToken(
        issuer: String,
        audience: String,
        subject: String,
        expiresAt: Instant = Instant.now().plusSeconds(600)
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(Date.from(Instant.now()))
            .expirationTime(Date.from(expiresAt))
            .jwtID(UUID.randomUUID().toString())
            .build()

        val header = JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(rsaJwk.keyID)
            .build()

        val signedJwt = SignedJWT(header, claims)
        signedJwt.sign(RSASSASigner(rsaJwk.toRSAPrivateKey()))
        return signedJwt.serialize()
    }

    private fun <T> withDiscoveryServer(block: (String) -> T): T {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        val issuer = "http://127.0.0.1:${server.address.port}"
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
        return try {
            block(issuer)
        } finally {
            server.stop(0)
        }
    }
}
