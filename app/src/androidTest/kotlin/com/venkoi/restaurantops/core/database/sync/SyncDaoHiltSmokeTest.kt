package com.venkoi.restaurantops.core.database.sync

import com.google.common.truth.Truth.assertThat
import com.venkoi.restaurantops.core.database.dao.SyncCursorDao
import com.venkoi.restaurantops.core.database.dao.SyncEntityMetadataDao
import com.venkoi.restaurantops.core.database.dao.SyncOutboxDao
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class SyncDaoHiltSmokeTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var metadataDao: SyncEntityMetadataDao
    @Inject lateinit var cursorDao: SyncCursorDao
    @Inject lateinit var outboxDao: SyncOutboxDao

    @Before
    fun inject() = hiltRule.inject()

    @Test
    fun syncDaosAreBoundAndOperational() = runBlocking {
        assertThat(metadataDao.getAll()).isEmpty()
        assertThat(cursorDao.getAll()).isEmpty()
        assertThat(outboxDao.getAll()).isEmpty()
    }
}
