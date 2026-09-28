package com.geno1024.ai.gits.data

import android.content.SharedPreferences

/**
 * The small part of key-value storage this app uses.
 *
 * Everything the app persists is a handful of strings, so this stays narrower than
 * SharedPreferences on purpose: a narrower interface is one that can be stood up over a
 * plain map, which is what lets the stores that hold secrets be tested off-device.
 */
interface Settings {

    fun string(key: String): String?

    fun strings(key: String): Set<String>?

    fun write(
        strings: Map<String, String> = emptyMap(),
        stringsSet: Map<String, Set<String>> = emptyMap(),
        removed: Set<String> = emptySet(),
    )
}

/** [Settings] over Android's [SharedPreferences]. */
class SharedPreferencesSettings(private val prefs: SharedPreferences) : Settings {

    override fun string(key: String): String? = prefs.getString(key, null)

    override fun strings(key: String): Set<String>? = prefs.getStringSet(key, null)

    override fun write(
        strings: Map<String, String>,
        stringsSet: Map<String, Set<String>>,
        removed: Set<String>,
    ) {
        val editor = prefs.edit()
        strings.forEach { (key, value) -> editor.putString(key, value) }
        stringsSet.forEach { (key, value) -> editor.putStringSet(key, value) }
        removed.forEach(editor::remove)
        editor.apply()
    }
}
