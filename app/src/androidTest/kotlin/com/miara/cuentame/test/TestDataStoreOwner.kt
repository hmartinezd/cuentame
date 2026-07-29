package com.miara.cuentame.test

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestDataStoreOwner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    val file: File = context.preferencesDataStoreFile("test_settings_${System.currentTimeMillis()}")

    val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { file }
    )

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    fun closeForProcessShutdown() {
        job.cancel()
    }
}
