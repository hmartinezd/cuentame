package com.miara.cuentame.core.preferences.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.miara.cuentame.core.preferences.model.ThemeMode
import com.miara.cuentame.feature.onboarding.model.OnboardingDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class DataStoreAppPreferencesRepositoryTest {

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreAppPreferencesRepository
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val testScope = TestScope(UnconfinedTestDispatcher() + Job())

    @Before
    fun setup() {
        val testDbName = "test_datastore_${System.currentTimeMillis()}"
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { context.preferencesDataStoreFile(testDbName) }
        )
        repository = DataStoreAppPreferencesRepository(dataStore, Json)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        File(context.filesDir, "datastore").deleteRecursively()
    }

    @Test
    fun saveAndObservePreferences() = runBlocking {
        repository.setThemeMode(ThemeMode.DARK)
        val prefs = repository.observePreferences().first()
        assertThat(prefs.themeMode).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun saveAndObserveDraft() = runBlocking {
        val draft = OnboardingDraft(restaurantName = "Test Rest")
        repository.saveOnboardingDraft(draft)
        val saved = repository.observeOnboardingDraft().first()
        assertThat(saved?.restaurantName).isEqualTo("Test Rest")
    }
}
