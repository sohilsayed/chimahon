package tachiyomi.data

import app.cash.sqldelight.ColumnAdapter
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import java.util.Date

object DateColumnAdapter : ColumnAdapter<Date, Long> {
    override fun decode(databaseValue: Long): Date = Date(databaseValue)
    override fun encode(value: Date): Long = value.time
}

private const val LIST_OF_STRINGS_SEPARATOR = ", "
object StringListColumnAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String) = if (databaseValue.isEmpty()) {
        emptyList()
    } else {
        databaseValue.split(LIST_OF_STRINGS_SEPARATOR)
    }
    override fun encode(value: List<String>) = value.joinToString(
        separator = LIST_OF_STRINGS_SEPARATOR,
    )
}

object UpdateStrategyColumnAdapter : ColumnAdapter<UpdateStrategy, Long> {
    override fun decode(databaseValue: Long): UpdateStrategy =
        UpdateStrategy.entries.getOrElse(databaseValue.toInt()) { UpdateStrategy.ALWAYS_UPDATE }

    override fun encode(value: UpdateStrategy): Long = value.ordinal.toLong()
}

object StringMapColumnAdapter : ColumnAdapter<Map<String, String>, String> {
    override fun decode(databaseValue: String): Map<String, String> {
        if (databaseValue.isEmpty() || databaseValue == "{}") return emptyMap()
        return try {
            val obj = org.json.JSONObject(databaseValue)
            buildMap {
                obj.keys().forEach { key ->
                    put(key, obj.getString(key))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    override fun encode(value: Map<String, String>): String {
        return org.json.JSONObject(value).toString()
    }
}

object StringSetColumnAdapter : ColumnAdapter<Set<String>, String> {
    override fun decode(databaseValue: String): Set<String> {
        if (databaseValue.isEmpty() || databaseValue == "[]") return emptySet()
        return try {
            val arr = org.json.JSONArray(databaseValue)
            buildSet {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        } catch (_: Exception) {
            emptySet()
        }
    }

    override fun encode(value: Set<String>): String {
        return org.json.JSONArray(value).toString()
    }
}

object JsonStringListColumnAdapter : ColumnAdapter<List<String>, String> {
    override fun decode(databaseValue: String): List<String> {
        if (databaseValue.isEmpty() || databaseValue == "[]") return emptyList()
        return try {
            val arr = org.json.JSONArray(databaseValue)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    override fun encode(value: List<String>): String {
        return org.json.JSONArray(value).toString()
    }
}
