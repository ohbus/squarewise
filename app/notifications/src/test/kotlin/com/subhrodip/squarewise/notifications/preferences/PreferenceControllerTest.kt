package com.subhrodip.squarewise.notifications.preferences

import com.subhrodip.squarewise.notifications.preferences.api.PreferenceController
import com.subhrodip.squarewise.notifications.preferences.model.NotificationPreferences
import com.subhrodip.squarewise.notifications.preferences.persistence.InMemoryPreferenceStore
import com.subhrodip.squarewise.notifications.preferences.persistence.JpaPreferenceStore
import com.subhrodip.squarewise.notifications.preferences.persistence.NotificationPreferenceRepository

import com.subhrodip.squarewise.errors.http.GlobalErrorHandler
import com.subhrodip.squarewise.ids.contracts.ApiEndpoints
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.RequestPostProcessor
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.security.Principal

class PreferenceControllerTest {
    private val mvc = MockMvcBuilders.standaloneSetup(PreferenceController(InMemoryPreferenceStore()))
        .setControllerAdvice(GlobalErrorHandler()).build()
    private val user = RequestPostProcessor { request -> request.userPrincipal = Principal { "alice" }; request }
    private val blankUser = RequestPostProcessor { request -> request.userPrincipal = Principal { "   " }; request }

    @Test
    fun `reads default preferences for authenticated user`() {
        mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_PREFERENCES).with(user))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.emailEnabled").value(true))
            .andExpect(jsonPath("$.pushEnabled").value(true))
    }

    @Test
    fun `updates and reads preferences`() {
        mvc.perform(
            put(ApiEndpoints.Notifications.V1.PATH_PREFERENCES)
                .with(user)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailEnabled\":false,\"pushEnabled\":true}")
        )
            .andExpect(status().isNoContent)

        val response = mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_PREFERENCES).with(user)).andReturn().response
        assertEquals("{\"emailEnabled\":false,\"pushEnabled\":true}", response.contentAsString)
    }

    @Test
    fun `rejects get preferences without authentication`() {
        mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_PREFERENCES))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects update preferences without authentication`() {
        mvc.perform(
            put(ApiEndpoints.Notifications.V1.PATH_PREFERENCES)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailEnabled\":false,\"pushEnabled\":true}")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects get preferences with blank subject`() {
        mvc.perform(get(ApiEndpoints.Notifications.V1.PATH_PREFERENCES).with(blankUser))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }

    @Test
    fun `rejects update preferences with blank subject`() {
        mvc.perform(
            put(ApiEndpoints.Notifications.V1.PATH_PREFERENCES)
                .with(blankUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emailEnabled\":false,\"pushEnabled\":true}")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("AUTHENTICATION_ERROR"))
    }
}
