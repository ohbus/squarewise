package com.subhrodip.squarewise.accounts.profile.service

import com.subhrodip.squarewise.accounts.errors.AccountsDomainException
import com.subhrodip.squarewise.errors.catalog.AccountsErrors
import java.time.DateTimeException
import java.time.ZoneId

object ProfileRules {
    private val subjectPattern = Regex("^[A-Za-z0-9|._:@-]{1,200}$")

    fun requireSubject(subject: String): String {
        if (!subjectPattern.matches(subject)) {
            throw AccountsDomainException(AccountsErrors.PROFILE_SUBJECT_INVALID)
        }
        return subject
    }

    fun requireTimezone(timezone: String): String {
        try {
            ZoneId.of(timezone)
        } catch (_: DateTimeException) {
            throw AccountsDomainException(AccountsErrors.TIMEZONE_INVALID)
        }
        return timezone
    }
}
