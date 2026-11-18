package com.example.cityfixmaas.data.validation

import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.RegistrationForm
import com.example.cityfixmaas.data.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ValidationTest {

    private val citizen = RegistrationForm(
        name = "Rui", email = "rui@example.com", password = "secret1",
        phone = "912 345 678", nif = "123456789", location = LatLng(40.2, -8.4),
    )

    @Test fun validNif() = assertNull(Validation.nif("123456789"))

    @Test fun nifWithWrongCheckDigit() = assertEquals("Invalid NIF", Validation.nif("123456780"))

    @Test fun nifWithRemainderBelowTwoUsesZero() {
        // 1*9 + 1*2 = 11, remainder 0, so the check digit is 0
        assertNull(Validation.nif("100000010"))
    }

    @Test fun nifMustHaveNineDigits() {
        assertNotNull(Validation.nif("12345678"))
        assertNotNull(Validation.nif("12345678a"))
    }

    @Test fun phoneAcceptsPrefixAndSpaces() {
        assertNull(Validation.phone("+351 912 345 678"))
        assertNull(Validation.phone("239123456"))
        assertEquals("912345678", Validation.normalizePhone("+351 912-345-678"))
    }

    @Test fun phoneRejectsOtherNumbers() {
        assertNotNull(Validation.phone("812345678"))
        assertNotNull(Validation.phone("91234567"))
    }

    @Test fun email() {
        assertNull(Validation.email(" rui@example.com "))
        assertNotNull(Validation.email("rui@example"))
        assertNotNull(Validation.email(""))
    }

    @Test fun validCitizenForm() = assertNull(Validation.registration(citizen, UserRole.CITIZEN))

    @Test fun citizenNeedsLocation() =
        assertEquals("Location is required", Validation.registration(citizen.copy(location = null), UserRole.CITIZEN))

    @Test fun operatorNeedsMunicipalEmail() {
        assertNotNull(Validation.registration(citizen, UserRole.OPERATOR))
        assertNull(Validation.registration(citizen.copy(email = "ana@CM-Coimbra.pt", location = null), UserRole.OPERATOR))
    }
}
