package com.miara.cuentame.core.backup.internal

import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.model.backup.BackupPreferencesDto
import com.miara.cuentame.core.preferences.model.AppPreferences
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class RestorePreferencesApplierTest {

    private val repository = mockk<AppPreferencesRepository>()
    private lateinit var applier: RestorePreferencesApplier

    @Before
    fun setup() {
        applier = RestorePreferencesApplier(repository)
    }

    @Test
    fun `apply sets all preference values`() = runTest {
        val dto = BackupPreferencesDto("DARK", false, "es-US", menuManagementEnabled = false)
        coEvery { repository.setThemeMode(any()) } just Runs
        coEvery { repository.setDynamicColorEnabled(any()) } just Runs
        coEvery { repository.setAppLocaleTag(any()) } just Runs
        coEvery { repository.setMenuManagementEnabled(any()) } just Runs
        
        applier.apply(dto)
        
        coVerify {
            repository.setThemeMode(ThemeMode.DARK)
            repository.setDynamicColorEnabled(false)
            repository.setAppLocaleTag("es-US")
            repository.setMenuManagementEnabled(false)
        }
    }

    @Test
    fun `captureRollback returns current preferences as DTO`() = runTest {
        val prefs = AppPreferences.DEFAULT.copy(
            onboardingCompleted = true,
            themeMode = ThemeMode.LIGHT,
            dynamicColorEnabled = true,
            appLocaleTag = "en-US",
            menuManagementEnabled = false
        )
        every { repository.observePreferences() } returns flowOf(prefs)
        
        val result = applier.captureRollback()
        
        val expected = BackupPreferencesDto("LIGHT", true, "en-US", menuManagementEnabled = false)
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `validate returns true for SYSTEM en-US`() {
        val dto = BackupPreferencesDto("SYSTEM", true, "en-US")
        assertThat(applier.validate(dto)).isTrue()
    }

    @Test
    fun `validate returns true for LIGHT es-US`() {
        val dto = BackupPreferencesDto("LIGHT", true, "es-US")
        assertThat(applier.validate(dto)).isTrue()
    }

    @Test
    fun `validate returns true for DARK en-US`() {
        val dto = BackupPreferencesDto("DARK", true, "en-US")
        assertThat(applier.validate(dto)).isTrue()
    }

    @Test
    fun `validate returns false for invalid theme`() {
        val dto = BackupPreferencesDto("GHOST", true, "en-US")
        assertThat(applier.validate(dto)).isFalse()
    }

    @Test
    fun `validate returns false for unsupported locale`() {
        val dto = BackupPreferencesDto("LIGHT", true, "fr-FR")
        assertThat(applier.validate(dto)).isFalse()
    }

    @Test
    fun `validate returns false for empty locale`() {
        val dto = BackupPreferencesDto("LIGHT", true, "")
        assertThat(applier.validate(dto)).isFalse()
    }

    @Test
    fun `apply rejects invalid preferences without repository writes`() = runTest {
        val dto = BackupPreferencesDto("INVALID", true, "fr-FR")
        
        // validate should be false
        assertThat(applier.validate(dto)).isFalse()
        
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                applier.apply(dto)
            }
        }

        coVerify(exactly = 0) {
            repository.setThemeMode(any())
            repository.setDynamicColorEnabled(any())
            repository.setAppLocaleTag(any())
            repository.setMenuManagementEnabled(any())
        }
    }
}
