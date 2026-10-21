package io.github.mdrzn.cleanframetv

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Optional: opens a chosen app or input when an assigned remote button is pressed (for example the
 * Mi or Netflix key, which TV vendors handle before any app sees them). Accessibility services get
 * key presses first; this one acts only on the assigned keycodes and passes every other key through.
 * It receives no accessibility events and cannot read the screen. Settings → Android TV → Remote
 * buttons assigns keys through [learn]: the next assignable key goes to the interface instead.
 */
class KeyRemapService : AccessibilityService() {
    private lateinit var catalog: AndroidCatalog
    @Volatile private var buttons: Map<Int, String> = emptyMap()
    @Volatile private var debug = false
    private val prefs by lazy { getSharedPreferences("cleanframe", Context.MODE_PRIVATE) }
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AndroidCatalog.BUTTONS) buttons = catalog.buttons()
        if (key == HomeActivity.DEBUG) debug = prefs.getBoolean(HomeActivity.DEBUG, false)
    }

    override fun onServiceConnected() {
        catalog = AndroidCatalog(this)
        buttons = catalog.buttons()
        debug = prefs.getBoolean(HomeActivity.DEBUG, false)
        prefs.registerOnSharedPreferenceChangeListener(changes)
        running = true
        Log.i(AndroidCatalog.TAG, "Remote buttons active (${buttons.size} assigned)")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        // A learned key is swallowed until it is released, so its long press can't reach the TV either.
        if (code == swallowed) {
            if (event.action == KeyEvent.ACTION_UP) swallowed = 0
            return true
        }
        val learning = learner
        if (learning != null && Buttons.assignable(code)) {
            learner = null
            if (System.currentTimeMillis() <= learnUntil && event.action == KeyEvent.ACTION_DOWN) {
                swallowed = code
                learning(code)
                return true
            }
        }
        val target = buttons[code]
        if (target == null) {
            // Debug mode only: shows which keycode a button sends, for assigning it (docs/install.md).
            if (debug && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) Log.i(AndroidCatalog.TAG, "Key ${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)})")
            return false
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val error = catalog.launch(target)
            if (error.isNotEmpty()) Log.w(AndroidCatalog.TAG, "Button ${event.keyCode} → $target: $error")
        }
        return true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {}

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        learner = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        prefs.unregisterOnSharedPreferenceChangeListener(changes)
        super.onDestroy()
    }

    companion object {
        /** Whether the service is enabled and connected; Settings shows it. */
        @Volatile var running = false
            private set
        @Volatile private var learner: ((Int) -> Unit)? = null
        @Volatile private var learnUntil = 0L
        @Volatile private var swallowed = 0

        /** Hands the next assignable key pressed within [timeoutMs] to [onKey] instead of its usual job. */
        fun learn(timeoutMs: Long, onKey: (Int) -> Unit) {
            learnUntil = System.currentTimeMillis() + timeoutMs
            learner = onKey
        }

        fun cancelLearning() { learner = null }
    }
}
