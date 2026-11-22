package com.example.cityfixmaas.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {

    @Test
    fun name_validatesRequired() {
        assertEquals(FieldError.EMPTY, Validation.name(""))
        assertEquals(FieldError.EMPTY, Validation.name("   "))
        assertNull(Validation.name("Rui Silva"))
    }

    @Test
    fun email_validatesGeneralAndMunicipal() {
        assertEquals(FieldError.EMPTY, Validation.email(""))
        assertEquals(FieldError.EMAIL_INVALID, Validation.email("not-an-email"))
        assertNull(Validation.email("citizen@example.com"))

        // Municipal operator check
        assertNull(Validation.email("operator@cm-coimbra.pt", municipalOnly = true))
        assertEquals(
            FieldError.EMAIL_NOT_MUNICIPAL,
            Validation.email("operator@gmail.com", municipalOnly = true),
        )
    }

    @Test
    fun password_validatesLengthAndMatch() {
        assertEquals(FieldError.EMPTY, Validation.password(""))
        assertEquals(FieldError.PASSWORD_SHORT, Validation.password("12345"))
        assertNull(Validation.password("123456"))

        // Match
        assertEquals(FieldError.EMPTY, Validation.passwordMatch("password", ""))
        assertEquals(FieldError.PASSWORD_MISMATCH, Validation.passwordMatch("password", "different"))
        assertNull(Validation.passwordMatch("password", "password"))
    }

    @Test
    fun phone_validatesNineDigits() {
        assertEquals(FieldError.EMPTY, Validation.phone(""))
        assertEquals(FieldError.PHONE_INVALID, Validation.phone("12345678")) // 8 digits
        assertEquals(FieldError.PHONE_INVALID, Validation.phone("1234567890")) // 10 digits
        assertEquals(FieldError.PHONE_INVALID, Validation.phone("91234567a")) // non-digit
        assertNull(Validation.phone("912345678"))
    }

    @Test
    fun nif_validatesPortugueseChecksum() {
        assertEquals(FieldError.EMPTY, Validation.nif(""))
        assertEquals(FieldError.NIF_INVALID, Validation.nif("12345678"))
        assertEquals(FieldError.NIF_INVALID, Validation.nif("1234567890"))
        assertEquals(FieldError.NIF_INVALID, Validation.nif("123456780")) // invalid check digit
        assertNull(Validation.nif("123456789")) // valid check digit (9)
    }
}
