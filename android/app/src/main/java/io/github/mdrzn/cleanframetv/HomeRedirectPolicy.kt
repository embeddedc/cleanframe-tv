package io.github.mdrzn.cleanframetv

/** Pure, deliberately narrow rules for the opt-in Google TV Home fallback. */
object HomeRedirectPolicy {
    const val GOOGLE_TV = "com.google.android.apps.tv.launcherx"
    private val homes = setOf(
        "$GOOGLE_TV.home.HomeActivity",
        "$GOOGLE_TV.home.VanillaModeHomeActivity"
    )
    private const val WINDOW_MS = 10_000L
    private const val COOLDOWN_MS = 1_500L
    private const val MAX_ATTEMPTS = 3

    enum class Action { IGNORE, OPEN, PAUSE }
    data class Decision(val action: Action, val attempts: List<Long>)

    fun decide(
        enabled: Boolean, packageName: String?, className: String?,
        interactive: Boolean, locked: Boolean, homeVisible: Boolean,
        now: Long, attempts: List<Long>
    ): Decision {
        if (!enabled || packageName != GOOGLE_TV || className !in homes ||
            !interactive || locked || homeVisible) return Decision(Action.IGNORE, attempts)
        // Clock changes and malformed persisted state fail closed rather than enabling a loop.
        if (attempts.any { it > now || it < 0 }) return Decision(Action.PAUSE, attempts)
        val recent = attempts.filter { now - it < WINDOW_MS }.sorted()
        if (recent.isNotEmpty() && now - recent.last() < COOLDOWN_MS) {
            return Decision(Action.IGNORE, recent)
        }
        if (recent.size >= MAX_ATTEMPTS) return Decision(Action.PAUSE, recent)
        return Decision(Action.OPEN, recent + now)
    }
}
