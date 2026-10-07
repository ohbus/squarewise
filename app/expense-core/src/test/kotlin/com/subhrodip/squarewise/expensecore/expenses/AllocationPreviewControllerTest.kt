package com.subhrodip.squarewise.expensecore.expenses
import com.subhrodip.squarewise.expensecore.expenses.api.AllocationPreviewController
import com.subhrodip.squarewise.expensecore.expenses.api.request.AllocationPreviewRequest
import com.subhrodip.squarewise.expensecore.expenses.domain.AllocationCalculator
import com.subhrodip.squarewise.errors.exceptions.SquarewiseException

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import com.subhrodip.squarewise.errors.http.GlobalErrorHandler

import com.subhrodip.squarewise.ids.contracts.ApiEndpoints

class AllocationPreviewControllerTest {
    private val mvc: MockMvc = MockMvcBuilders.standaloneSetup(AllocationPreviewController())
        .setControllerAdvice(GlobalErrorHandler()).build()

    @Test
    fun `preview returns exact deterministic allocation as strings`() {
        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.PATH_ALLOCATION_PREVIEW)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"totalMinor":"100","participantIds":["c","a","b"]}""")
        ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalMinor").value("100"))
            .andExpect(jsonPath("$.allocations.a").value("34"))
            .andExpect(jsonPath("$.allocations.b").value("33"))
            .andExpect(jsonPath("$.allocations.c").value("33"))
    }

    @Test
    fun `calculator output always sums to request total`() {
        val allocations = AllocationCalculator.equal(101, listOf("a", "b", "c"))
        assertEquals(101L, allocations.values.sum())
    }

    @Test
    fun `invalid participant list returns bad request`() {
        mvc.perform(
            post(ApiEndpoints.ExpenseCore.V1.PATH_ALLOCATION_PREVIEW)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"totalMinor":"100","participantIds":[]}""")
        ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.requestId").value("missing-request-id"))
    }

    @Test
    fun `preview rejects a non numeric total before allocation`() {
        assertThrows(SquarewiseException::class.java) {
            AllocationPreviewController().preview(
                AllocationPreviewRequest("not-a-number", listOf("a"))
            )
        }
    }

    @Test
    fun `preview rejects a negative total before allocation`() {
        assertThrows(SquarewiseException::class.java) {
            AllocationPreviewController().preview(
                AllocationPreviewRequest("-1", listOf("a"))
            )
        }
    }

    @Test
    fun `preview maps allocation calculator rejection to the stable error`() {
        val error = assertThrows(SquarewiseException::class.java) {
            AllocationPreviewController().preview(
                AllocationPreviewRequest("100", listOf("alice", "alice"))
            )
        }

        assertEquals("VALIDATION_FAILED", error.definition.legacyCode)
        assertEquals("participant IDs must be unique", error.message)
    }
}
