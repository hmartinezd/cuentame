package com.miara.cuentame.core.preferences.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.miara.cuentame.core.preferences.datastore.DataStoreAppPreferencesRepository
import com.miara.cuentame.core.preferences.repository.AppPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PreferencesModule::class]
)
abstract class TestPreferencesModule {

    @Binds
    @Singleton
    abstract fun bindAppPreferencesRepository(
        impl: DataStoreAppPreferencesRepository
    ): AppPreferencesRepository

    companion object {
        private var dataStoreInstance: DataStore<Preferences>? = null

        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return dataStoreInstance ?: PreferenceDataStoreFactory.create(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                produceFile = { context.preferencesDataStoreFile("test_settings_${System.currentTimeMillis()}") }
            ).also { dataStoreInstance = it }
        }

        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }
}
