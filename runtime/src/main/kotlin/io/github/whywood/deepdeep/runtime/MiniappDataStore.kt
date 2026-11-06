package io.github.whywood.deepdeep.runtime

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

interface MiniappData {
    suspend fun get(key: String): JsonElement?

    suspend fun set(key: String, value: JsonElement)

    suspend fun remove(key: String): Boolean

    suspend fun keys(): List<String>

    fun close()
}

class SqliteMiniappData(private val databaseFile: File) : MiniappData {
    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private var database: SQLiteDatabase? = null

    override suspend fun get(key: String): JsonElement? = withContext(Dispatchers.IO) {
        synchronized(lock) { database().read(key) }
    }

    override suspend fun set(key: String, value: JsonElement) {
        withContext(Dispatchers.IO) {
            synchronized(lock) { database().write(key, value.toString()) }
        }
    }

    override suspend fun remove(key: String): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) { database().delete(TABLE, "$COLUMN_KEY = ?", arrayOf(key)) > 0 }
    }

    override suspend fun keys(): List<String> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            database().query(
                TABLE,
                arrayOf(COLUMN_KEY),
                null,
                null,
                null,
                null,
                "$COLUMN_KEY ASC",
            ).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed.compareAndSet(false, true)) {
                database?.close()
                database = null
            }
        }
    }

    private fun database(): SQLiteDatabase {
        check(!closed.get()) { "miniapp data store is closed" }
        database?.let { return it }
        val parent = databaseFile.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw IllegalStateException("cannot create miniapp data directory: ${parent.absolutePath}")
        }
        val opened = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        opened.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE ($COLUMN_KEY TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL, updated_at INTEGER NOT NULL)",
        )
        database = opened
        return opened
    }

    private fun SQLiteDatabase.read(key: String): JsonElement? =
        query(
            TABLE,
            arrayOf("value"),
            "$COLUMN_KEY = ?",
            arrayOf(key),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val stored = cursor.getString(0)
            runCatching { Json.parseToJsonElement(stored) }.getOrNull()
        }

    private fun SQLiteDatabase.write(key: String, value: String) {
        val values = ContentValues().apply {
            put(COLUMN_KEY, key)
            put("value", value)
            put("updated_at", System.currentTimeMillis())
        }
        insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private companion object {
        const val TABLE = "kv"
        const val COLUMN_KEY = "key"
    }
}
