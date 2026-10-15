@file:Suppress("CanConvertToMultiDollarString")

package com.subhrodip.squarewise.expensecore.recurring.service

import com.subhrodip.squarewise.db.routing.DbWriter
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled background worker that periodically polls for due recurring expense schedules
 * and executes bounded catch-up occurrence generation.
 */
@Component
class RecurringExpenseWorker(
    private val service: RecurringExpenseService,
    @Value("\${squarewise.recurring.enabled:false}") var enabled: Boolean = false,
    @Value("\${squarewise.recurring.max-catch-up-occurrences:12}") var maxCatchUpOccurrences: Int = 12
) {
    @Scheduled(fixedDelayString = "\${squarewise.recurring.poll-delay-ms:60000}")
    @DbWriter("recurring.worker")
    fun run(): Int {
        if (!enabled) {
            return 0
        }
        return service.processDueOccurrences(maxCatchUpOccurrences)
    }
}
