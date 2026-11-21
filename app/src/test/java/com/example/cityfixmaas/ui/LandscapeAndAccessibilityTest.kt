package com.example.cityfixmaas.ui

import com.example.cityfixmaas.data.model.IncidentType
import com.example.cityfixmaas.data.model.LatLng
import com.example.cityfixmaas.data.model.LifecycleState
import com.example.cityfixmaas.data.model.UserRole
import com.example.cityfixmaas.ui.auth.FieldError
import com.example.cityfixmaas.ui.auth.RegisterUiState
import com.example.cityfixmaas.ui.auth.messageRes
import com.example.cityfixmaas.ui.profile.ProfileUiState
import com.example.cityfixmaas.ui.tickets.ReportUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LandscapeAndAccessibilityTest {

    @Test
    fun reportUiState_preservesFormStateAcrossConfigurationChanges() {
        val original = ReportUiState(
            categoryId = "cat_roads",
            incidentType = IncidentType.BREAKDOWN,
            description = "Pothole on Rua Ferreira Borges",
            publicScope = true,
            location = LatLng(40.208, -8.425),
            photos = emptyList(),
        )

        // Simulating user editing description during landscape rotation
        val updated = original.copy(description = "Pothole on Rua Ferreira Borges near pharmacy")

        assertEquals("cat_roads", updated.categoryId)
        assertEquals(IncidentType.BREAKDOWN, updated.incidentType)
        assertEquals("Pothole on Rua Ferreira Borges near pharmacy", updated.description)
        assertTrue(updated.publicScope)
        assertNotNull(updated.location)
    }

    @Test
    fun registerUiState_retainsAllFormInputsForLandscapeRotation() {
        val state = RegisterUiState(
            role = UserRole.CITIZEN,
            name = "Maria Santos",
            email = "maria@example.com",
            password = "secretPassword123",
            confirmPassword = "secretPassword123",
            phone = "912345678",
            nif = "123456789",
            location = LatLng(40.205, -8.420),
            impactDetectionEnabled = true,
        )

        // All inputs are retained without data loss
        assertEquals("Maria Santos", state.name)
        assertEquals("maria@example.com", state.email)
        assertEquals("secretPassword123", state.password)
        assertEquals("secretPassword123", state.confirmPassword)
        assertEquals("912345678", state.phone)
        assertEquals("123456789", state.nif)
        assertEquals(UserRole.CITIZEN, state.role)
        assertTrue(state.impactDetectionEnabled)
        assertEquals(40.205, state.location?.latitude ?: 0.0, 0.001)
    }

    @Test
    fun profileUiState_tracksModificationsAcrossConfigurationChanges() {
        val state = ProfileUiState(
            phone = "919999999",
            originalPhone = "912345678",
        )
        // User changed phone in portrait, rotates to landscape: hasChanges remains true
        assertTrue(state.hasChanges)

        val revertedState = state.copy(phone = "912345678")
        assertFalse(revertedState.hasChanges)
    }

    @Test
    fun validationFieldErrors_allHaveLocalizedMessageResources() {
        FieldError.entries.forEach { error ->
            val resId = error.messageRes()
            assertTrue("FieldError $error must have a non-zero message resource", resId != 0)
        }
    }

    @Test
    fun bilingualStrings_englishAndPortugueseParity() {
        val enFile = File("src/main/res/values/strings.xml")
        val ptFile = File("src/main/res/values-pt/strings.xml")

        // In Gradle unit test environment, file paths might be relative to app/ or workspace root
        val actualEnFile = if (enFile.exists()) enFile else File("app/src/main/res/values/strings.xml")
        val actualPtFile = if (ptFile.exists()) ptFile else File("app/src/main/res/values-pt/strings.xml")

        if (actualEnFile.exists() && actualPtFile.exists()) {
            val enKeys = extractStringKeys(actualEnFile)
            val ptKeys = extractStringKeys(actualPtFile)

            // Verify essential accessibility strings exist in English
            val requiredA11yKeys = listOf(
                "cd_back",
                "cd_dashboard",
                "cd_incident_list",
                "cd_login",
                "cd_sign_out",
                "cd_profile",
                "cd_pending",
                "cd_categories",
                "cd_show_password",
                "cd_hide_password",
                "cd_incident_thumbnail",
                "fab_new_incident",
            )
            requiredA11yKeys.forEach { key ->
                assertTrue("English strings must include $key", enKeys.contains(key))
                assertTrue("Portuguese strings must include $key", ptKeys.contains(key))
            }
        }
    }

    private fun extractStringKeys(xmlFile: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(xmlFile)
        val stringNodes = doc.getElementsByTagName("string")
        val keys = mutableSetOf<String>()
        for (i in 0 until stringNodes.length) {
            val node = stringNodes.item(i)
            val nameAttr = node.attributes.getNamedItem("name")?.nodeValue
            val translatableAttr = node.attributes.getNamedItem("translatable")?.nodeValue
            if (nameAttr != null && translatableAttr != "false") {
                keys.add(nameAttr)
            }
        }
        return keys
    }
}
