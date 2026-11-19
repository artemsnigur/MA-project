package com.example.cityfixmaas.ui.auth

import androidx.annotation.StringRes
import com.example.cityfixmaas.R
import com.example.cityfixmaas.data.AppConfig

enum class FieldError {
    EMPTY,
    EMAIL_INVALID,
    EMAIL_NOT_MUNICIPAL,
    PASSWORD_SHORT,
    PASSWORD_MISMATCH,
    PHONE_INVALID,
    NIF_INVALID,
    LOCATION_REQUIRED
}

object Validation {
    const val MIN_PASSWORD_LENGTH = 6

    private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    fun name(value: String): FieldError? =
        if (value.isBlank()) FieldError.EMPTY else null

    /** Operators must use the municipal domain; citizens any valid email. */
    fun email(value: String, municipalOnly: Boolean = false): FieldError? {
        val email = value.trim()
        if (email.isEmpty()) return FieldError.EMPTY
        if (!EMAIL_REGEX.matches(email)) return FieldError.EMAIL_INVALID
        if (municipalOnly) {
            val domain = AppConfig.MUNICIPAL_EMAIL_DOMAIN.removePrefix("@").lowercase()
            if (!email.lowercase().endsWith("@$domain")) return FieldError.EMAIL_NOT_MUNICIPAL
        }
        return null
    }

    /** checkLength = false for sign-in (only "not empty"), true when creating an account. */
    fun password(value: String, checkLength: Boolean = true): FieldError? = when {
        value.isEmpty() -> FieldError.EMPTY
        checkLength && value.length < MIN_PASSWORD_LENGTH -> FieldError.PASSWORD_SHORT
        else -> null
    }

    fun passwordMatch(password: String, confirmation: String): FieldError? =
        if (confirmation.isEmpty()) FieldError.EMPTY
        else if (confirmation != password) FieldError.PASSWORD_MISMATCH
        else null

    /** Portuguese format: exactly 9 digits. */
    fun phone(value: String): FieldError? {
        val phone = value.trim()
        if (phone.isEmpty()) return FieldError.EMPTY
        return if (phone.length == 9 && phone.all { it.isDigit() }) null else FieldError.PHONE_INVALID
    }

    /** 9 digits plus the Portuguese check digit (weights 9..2 on the first 8 digits, modulo 11). */
    fun nif(value: String): FieldError? {
        val nif = value.trim()
        if (nif.isEmpty()) return FieldError.EMPTY
        if (nif.length != 9 || !nif.all { it.isDigit() }) return FieldError.NIF_INVALID
        val digits = nif.map { it.digitToInt() }
        val sum = (0..7).sumOf { digits[it] * (9 - it) }
        val check = (11 - sum % 11).let { if (it >= 10) 0 else it }
        return if (check == digits[8]) null else FieldError.NIF_INVALID
    }
}

@StringRes
fun FieldError.messageRes(): Int = when (this) {
    FieldError.EMPTY -> R.string.validation_required
    FieldError.EMAIL_INVALID -> R.string.validation_email_invalid
    FieldError.EMAIL_NOT_MUNICIPAL -> R.string.validation_email_not_municipal
    FieldError.PASSWORD_SHORT -> R.string.validation_password_short
    FieldError.PASSWORD_MISMATCH -> R.string.validation_password_mismatch
    FieldError.PHONE_INVALID -> R.string.validation_phone_invalid
    FieldError.NIF_INVALID -> R.string.validation_nif_invalid
    FieldError.LOCATION_REQUIRED -> R.string.validation_location_required
}
