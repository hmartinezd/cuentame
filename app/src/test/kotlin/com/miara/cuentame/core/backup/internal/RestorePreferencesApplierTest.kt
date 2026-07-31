package com.miara.cuentame.core.backup.internal

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
        val dto = BackupPreferencesDto("DARK", false, "es-US")
        coEvery { repository.setThemeMode(any()) } just Runs
        coEvery { repository.setDynamicColorEnabled(any()) } just Runs
        coEvery { repository.setAppLocaleTag(any()) } just Runs
        
        applier.apply(dto)
        
        coVerify {
            repository.setThemeMode(ThemeMode.DARK)
            repository.setDynamicColorEnabled(false)
            repository.setAppLocaleTag("es-US")
        }
    }

    @Test
    fun `captureRollback returns current preferences as DTO`() = runTest {
        val prefs = AppPreferences(true, ThemeMode.LIGHT, true, "en-US")
        every { repository.observePreferences() } returns flowOf(prefs)
        
        val result = applier.captureRollback()
        
        val expected = BackupPreferencesDto("LIGHT", true, "en-US")
        assert(result == expected)
    }

    @Test
    fun `validate returns true for valid theme and locale`() {
        val dto = BackupPreferencesDto("LIGHT", true, "en-US")
        assert(applier.validate(dto))
    }

    @Test
    fun `validate returns false for invalid theme`() {
        val dto = BackupPreferencesDto("GHOST", true, "en-US")
        assert(!applier.validate(dto))
    }

    @Test
    fun `validate returns false for unsupported locale`() {
        val dto = BackupPreferencesDto("LIGHT", true, "fr-FR")
        assert(!applier.validate(dto))
    }

    @Test
    fun `validate returns false for empty locale`() {
        val dto = BackupPreferencesDto("LIGHT", true, "")
        assert(!applier.validate(dto))
    }
}
