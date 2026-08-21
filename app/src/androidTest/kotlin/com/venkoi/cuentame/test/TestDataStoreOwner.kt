package com.venkoi.cuentame.test

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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TestDataStoreOwner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val dataStore: DataStore<Preferences>
        get() = INSTANCE ?: synchronized(this) {
            INSTANCE ?: createDataStore(context).also { INSTANCE = it }
        }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    fun closeForProcessShutdown() {
        // Keeps process singleton alive throughout instrumentation test suite
    }

    companion object {
        @Volatile
        private var INSTANCE: DataStore<Preferences>? = null
        private val job = SupervisorJob()
        private val scope = CoroutineScope(Dispatchers.IO + job)

        private fun createDataStore(context: Context): DataStore<Preferences> {
            val file = context.preferencesDataStoreFile("cuentame-instrumentation-test")
            if (file.exists()) {
                file.delete()
            }
            return PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file }
            )
        }
    }
}
