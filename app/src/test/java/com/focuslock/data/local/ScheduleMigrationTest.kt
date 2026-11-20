package com.focuslock.data.local

import android.os.CancellationSignal
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduleMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FocusDatabase::class.java,
        listOf(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun `migrate 9 to 10 creates schedule table and keeps sessions`() {
        helper.createDatabase(TEST_DB, 9).apply {
            execSQL(
                "INSERT INTO sessions (status, started_at, expires_at, unlocked_early, created_at) " +
                    "VALUES ('COMPLETED', 1, 2, 0, 3)"
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DB, 10, true).apply {
            val signal = CancellationSignal()
            val cursor = query(SimpleSQLiteQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'schedule_windows'"), signal)
            assertTrue(cursor.moveToFirst())
            cursor.close()
            val sessions = query(SimpleSQLiteQuery("SELECT COUNT(*) FROM sessions"), signal)
            sessions.moveToFirst()
            assertEquals(1, sessions.getInt(0))
            sessions.close()
            close()
        }
    }

    private companion object {
        const val TEST_DB = "migration-schedule-test"
    }
}
