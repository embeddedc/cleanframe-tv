package io.github.mdrzn.cleanframetv

import org.junit.Assert.*
import org.junit.Test

class HomeRedirectPolicyTest {
    private val google = HomeRedirectPolicy.GOOGLE_TV
    private fun decide(
        enabled: Boolean = true, pkg: String? = google,
        activity: String? = "$google.home.VanillaModeHomeActivity",
        interactive: Boolean = true, locked: Boolean = false, visible: Boolean = false,
        now: Long = 10_000L, history: List<Long> = emptyList()
    ) = HomeRedirectPolicy.decide(enabled, pkg, activity, interactive, locked, visible, now, history)

    @Test fun `both Google TV home modes open CleanFrame`() {
        assertEquals(HomeRedirectPolicy.Action.OPEN, decide().action)
        assertEquals(HomeRedirectPolicy.Action.OPEN, decide(activity = "$google.home.HomeActivity").action)
    }

    @Test fun `off means no interception`() {
        assertEquals(HomeRedirectPolicy.Action.IGNORE, decide(enabled = false).action)
    }

    @Test fun `only exact Google home components qualify`() {
        listOf(null, "", "android.widget.FrameLayout", "$google.profile.chooser.ProfileChooserActivity",
            "$google.dashboard.DashboardHandler", "$google.home.HomeActivityExtra").forEach {
            assertEquals(HomeRedirectPolicy.Action.IGNORE, decide(activity = it).action)
        }
        listOf(null, "", "com.tcl.settings", "com.google.android.tungsten.setupwraith",
            "io.github.mdrzn.cleanframetv", "io.github.mdrzn.cleanframetv.tcl").forEach {
            assertEquals(HomeRedirectPolicy.Action.IGNORE, decide(pkg = it).action)
        }
    }

    @Test fun `never wake the TV dismiss a lock screen or relaunch visible home`() {
        assertEquals(HomeRedirectPolicy.Action.IGNORE, decide(interactive = false).action)
        assertEquals(HomeRedirectPolicy.Action.IGNORE, decide(locked = true).action)
        assertEquals(HomeRedirectPolicy.Action.IGNORE, decide(visible = true).action)
    }

    @Test fun `duplicate events do not cause another launch or consume budget`() {
        val first = decide()
        val duplicate = decide(now = 10_100L, history = first.attempts)
        assertEquals(HomeRedirectPolicy.Action.IGNORE, duplicate.action)
        assertEquals(first.attempts, duplicate.attempts)
        assertEquals(HomeRedirectPolicy.Action.OPEN, decide(now = 11_500L, history = first.attempts).action)
    }

    @Test fun `repeated redirects trip the fuse even across service recreation`() {
        var persisted = emptyList<Long>()
        listOf(10_000L, 12_000L, 14_000L).forEach { now ->
            val decision = decide(now = now, history = persisted)
            assertEquals(HomeRedirectPolicy.Action.OPEN, decision.action)
            persisted = decision.attempts.toList()
        }
        assertEquals(HomeRedirectPolicy.Action.PAUSE, decide(now = 16_000L, history = persisted).action)
    }

    @Test fun `old launches expire and ordinary later Home use works`() {
        val decision = decide(now = 30_000L, history = listOf(10_000L, 12_000L, 14_000L))
        assertEquals(HomeRedirectPolicy.Action.OPEN, decision.action)
        assertEquals(listOf(30_000L), decision.attempts)
        assertEquals(HomeRedirectPolicy.Action.OPEN, decide(now = 20_000L, history = listOf(10_000L)).action)
    }

    @Test fun `backward clock or corrupted history pauses rather than clearing the fuse`() {
        assertEquals(HomeRedirectPolicy.Action.PAUSE, decide(history = listOf(20_000L)).action)
        assertEquals(HomeRedirectPolicy.Action.PAUSE, decide(history = listOf(-1L)).action)
    }

    @Test fun `ignored screens cannot reset the loop protection`() {
        val history = listOf(2_000L, 4_000L, 6_000L)
        val ignored = decide(activity = "$google.profile.chooser.ProfileChooserActivity", history = history)
        assertEquals(history, ignored.attempts)
        assertEquals(HomeRedirectPolicy.Action.PAUSE, decide(history = ignored.attempts).action)
    }

    @Test fun `unordered history still respects the most recent launch`() {
        assertEquals(HomeRedirectPolicy.Action.IGNORE, decide(history = listOf(9_500L, 5_000L)).action)
    }
}
