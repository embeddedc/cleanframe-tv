package io.github.mdrzn.cleanframetv

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Opt-in fallback for Google TV firmware that retains its own HOME handler. Watches only the
 * Google TV launcher's window-state events; no key filtering, view-tree access, polling or retries.
 */
class HomeCompatibilityService : AccessibilityService() {
    private val compatibility by lazy { HomeCompatibility(this) }

    override fun onServiceConnected() {
        running = true
        Log.i(AndroidCatalog.TAG, "Home compatibility service connected (opt-in=${compatibility.enabled})")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val decision = HomeRedirectPolicy.decide(
            compatibility.enabled, event.packageName?.toString(), event.className?.toString(),
            power.isInteractive, keyguard.isKeyguardLocked, HomeActivity.visible,
            System.currentTimeMillis(), compatibility.attempts
        )
        when (decision.action) {
            HomeRedirectPolicy.Action.IGNORE -> return
            HomeRedirectPolicy.Action.PAUSE -> {
                compatibility.pause(getString(R.string.home_compatibility_paused))
                Log.w(AndroidCatalog.TAG, "Home compatibility paused after repeated redirects or clock change")
            }
            HomeRedirectPolicy.Action.OPEN -> {
                if (!compatibility.record(decision.attempts)) {
                    compatibility.pause(getString(R.string.home_compatibility_save_failed))
                    return
                }
                try {
                    // Explicit component prevents Android resolving this back to Google TV. HOME
                    // category also avoids HomeActivity's ordinary-app forwarding path.
                    startActivity(Intent(this, HomeActivity::class.java)
                        .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                    Log.i(AndroidCatalog.TAG, "Home compatibility: opened CleanFrame from Google TV Home")
                } catch (e: RuntimeException) {
                    compatibility.pause(getString(R.string.home_compatibility_launch_failed))
                    Log.w(AndroidCatalog.TAG, "Home compatibility launch failed; paused", e)
                }
            }
        }
    }

    override fun onInterrupt() {
        compatibility.pause(getString(R.string.home_compatibility_interrupted))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        running = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    companion object {
        @Volatile var running = false
            private set
    }
}
