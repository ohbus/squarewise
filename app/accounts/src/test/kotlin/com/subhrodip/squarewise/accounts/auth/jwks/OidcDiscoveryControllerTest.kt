package com.subhrodip.squarewise.accounts.auth.jwks

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/** Unit test for OidcDiscoveryController checking JWKS and OpenID discovery contracts. */
class OidcDiscoveryControllerTest {

    private val rsaKeyProvider = DefaultRsaKeyProvider(RsaKeyProperties(keyId = "discovery-kid-1"))
    private val issuerUri = "https://accounts.squarewise.io"
    private val controller = OidcDiscoveryController(rsaKeyProvider, issuerUri)
    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    @Test
    fun `publishes RFC 7517 compliant JWKS at well known and accounts path`() {
        mockMvc.perform(get(ApiEndpoints.Accounts.V1.WELL_KNOWN_JWKS).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.keys").isArray)
            .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
            .andExpect(jsonPath("$.keys[0].use").value("sig"))
            .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
            .andExpect(jsonPath("$.keys[0].kid").value("discovery-kid-1"))
            .andExpect(jsonPath("$.keys[0].n").isString)
            .andExpect(jsonPath("$.keys[0].e").value("AQAB"))
            .andExpect(jsonPath("$.keys[0].d").doesNotExist()) // private key must never be exposed

        mockMvc.perform(get(ApiEndpoints.Accounts.V1.PATH_JWKS).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.keys[0].kid").value("discovery-kid-1"))
    }

    @Test
    fun `publishes RFC 8414 OpenID configuration metadata`() {
        mockMvc.perform(get(ApiEndpoints.Accounts.V1.WELL_KNOWN_OPENID_CONFIGURATION).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.issuer").value(issuerUri))
            .andExpect(jsonPath("$.jwks_uri").value("$issuerUri${ApiEndpoints.Accounts.V1.WELL_KNOWN_JWKS}"))
            .andExpect(jsonPath("$.id_token_signing_alg_values_supported[0]").value("RS256"))
    }

    @Test
    fun `uses the local issuer fallback when no issuer is configured`() {
        val fallbackController = OidcDiscoveryController(rsaKeyProvider, " ")
        val fallbackMvc = MockMvcBuilders.standaloneSetup(fallbackController).build()

        fallbackMvc.perform(
            get(ApiEndpoints.Accounts.V1.WELL_KNOWN_OPENID_CONFIGURATION)
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.issuer").value("http://localhost:28081"))
            .andExpect(
                jsonPath("$.jwks_uri").value(
                    "http://localhost:28081${ApiEndpoints.Accounts.V1.WELL_KNOWN_JWKS}"
                )
            )
    }
}
