package com.example.cityfixmaas.data.validation

import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.UserRole

/**
 * Form validation shared by the UI (inline errors) and the repositories (final check).
 * Each function returns an English error message, or null when the value is valid.
 */
object Validation {

    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    const val MIN_PASSWORD_LENGTH = 6

    fun name(value: String): String? =
        if (value.isBlank()) "Name is required" else null

    fun email(value: String): String? = when {
        value.isBlank() -> "Email is required"
        !EMAIL.matches(value.trim()) -> "Enter a valid email"
        else -> null
    }

    fun password(value: String): String? =
        if (value.length < MIN_PASSWORD_LENGTH) "Password must have at least $MIN_PASSWORD_LENGTH characters" else null

    /** Portuguese number: 9 digits starting with 2 or 9, optional +351 prefix, spaces allowed. */
    fun phone(value: String): String? {
        val digits = normalizePhone(value)
        return if (digits.length == 9 && digits[0] in "29" && digits.all(Char::isDigit)) null
        else "Enter a valid Portuguese phone number"
    }

    /** Portuguese NIF: 9 digits, the last one a mod-11 check digit. */
    fun nif(value: String): String? {
        val nif = value.trim()
        if (nif.length != 9 || !nif.all(Char::isDigit)) return "NIF must have 9 digits"
        val sum = (0 until 8).sumOf { i -> (nif[i] - '0') * (9 - i) }
        val remainder = sum % 11
        val check = if (remainder < 2) 0 else 11 - remainder
        return if (nif[8] - '0' == check) null else "Invalid NIF"
    }

    fun municipalEmail(value: String): String? =
        if (value.trim().lowercase().endsWith("@" + AppConfig.MUNICIPAL_EMAIL_DOMAIN)) null
        else "Operators must use an @${AppConfig.MUNICIPAL_EMAIL_DOMAIN} email"

    /** First problem in the form, or null. Citizens must provide a location. */
    fun registration(form: RegistrationForm, role: UserRole): String? =
        name(form.name)
            ?: email(form.email)
            ?: (if (role == UserRole.OPERATOR) municipalEmail(form.email) else null)
            ?: password(form.password)
            ?: phone(form.phone)
            ?: nif(form.nif)
            ?: (if (role == UserRole.CITIZEN && form.location == null) "Location is required" else null)

    /** Stored form of a phone number: digits only, without the +351 prefix. */
    fun normalizePhone(value: String): String =
        value.filterNot { it.isWhitespace() || it == '-' }.removePrefix("+351").removePrefix("00351")
}
