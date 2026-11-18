package com.example.cityfixmaas.data.validation

import com.example.cityfixmaas.data.AppConfig
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.UserRole

/**
 * Form checks used by the screens (inline errors) and by the repositories.
 * Each function returns an error message, or null when the value is fine.
 */
object Validation {

    fun name(value: String): String? =
        if (value.isBlank()) "Name is required" else null

    fun email(value: String): String? =
        if (Regex("^\\S+@\\S+\\.\\S+$").matches(value.trim())) null else "Enter a valid email"

    /** Firebase Auth needs at least 6 characters. */
    fun password(value: String): String? =
        if (value.length >= 6) null else "Password must have at least 6 characters"

    fun phone(value: String): String? =
        if (Regex("^\\d{9}$").matches(value.trim())) null else "Phone must have 9 digits"

    fun nif(value: String): String? =
        if (Regex("^\\d{9}$").matches(value.trim())) null else "NIF must have 9 digits"

    fun municipalEmail(value: String): String? =
        if (value.trim().lowercase().endsWith("@" + AppConfig.MUNICIPAL_EMAIL_DOMAIN)) null
        else "Operators must use an @${AppConfig.MUNICIPAL_EMAIL_DOMAIN} email"

    /** The first problem in the form, or null when everything is valid. */
    fun registration(form: RegistrationForm, role: UserRole): String? =
        name(form.name)
            ?: email(form.email)
            ?: (if (role == UserRole.OPERATOR) municipalEmail(form.email) else null)
            ?: password(form.password)
            ?: phone(form.phone)
            ?: nif(form.nif)
            ?: (if (role == UserRole.CITIZEN && form.location == null) "Location is required" else null)
}
