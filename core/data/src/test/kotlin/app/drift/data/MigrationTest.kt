package app.drift.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.drift.data.db.DriftDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Scaffold for schema migrations. Schemas are exported to core/data/schemas; when version 2 ships, add
 * `MIGRATION_1_2` to [migrations] and a test that creates version 1, inserts rows, runs the migration and
 * validates against the version 2 schema. Every shipped version must keep a test here (plan 9.3).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DriftDatabase::class.java)

    private val migrations: Array<androidx.room.migration.Migration> = emptyArray()

    @Test fun version1SchemaCreatesAndValidates() {
        helper.createDatabase("migration-test", 1).use { db ->
            db.execSQL("INSERT INTO engine_state (id, payload) VALUES (1, '{}')")
        }
        helper.runMigrationsAndValidate("migration-test", 1, true, *migrations).use { db ->
            db.query("SELECT payload FROM engine_state WHERE id = 1").use { c ->
                check(c.moveToFirst() && c.getString(0) == "{}")
            }
        }
    }
}
