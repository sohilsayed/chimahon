package eu.kanade.tachiyomi.animesource.utils

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import eu.kanade.tachiyomi.animesource.AnimeSource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

fun preferencesKey(id: Long) = "source_$id"

fun AnimeSource.preferencesKey(): String = preferencesKey(id)

fun sourcePreferences(key: String): SharedPreferences =
    Injekt.get<Application>().getSharedPreferences(key, Context.MODE_PRIVATE)

fun AnimeSource.sourcePreferences(): SharedPreferences = sourcePreferences(preferencesKey())

fun sourcePreferences(id: Long): SharedPreferences = sourcePreferences(preferencesKey(id))
