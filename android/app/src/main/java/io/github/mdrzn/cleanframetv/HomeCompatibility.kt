package io.github.mdrzn.cleanframetv

import android.content.Context

/** Separate opt-in from the Android service permission. Never changes the system launcher. */
class HomeCompatibility(context: Context) {
    private val prefs = context.getSharedPreferences("home_compatibility", Context.MODE_PRIVATE)
    val enabled: Boolean get() = prefs.getBoolean("enabled", false)
    val reason: String get() = prefs.getString("reason", "").orEmpty()
    val attempts: List<Long> get() {
        val saved = prefs.getString("attempts", "").orEmpty()
        if (saved.isEmpty()) return emptyList()
        // -1 makes the policy pause on an unreadable history instead of resetting its limit.
        return saved.split(',').map { it.toLongOrNull() ?: -1L }
    }

    fun enable() = prefs.edit().putBoolean("enabled", true)
        .remove("reason").remove("attempts").commit()

    fun pause(reason: String = "") {
        prefs.edit().putBoolean("enabled", false).putString("reason", reason).commit()
    }

    // Persist before launching: a crashing/recreated process must not reset the loop limit.
    fun record(attempts: List<Long>): Boolean =
        prefs.edit().putString("attempts", attempts.joinToString(",")).commit()
}
