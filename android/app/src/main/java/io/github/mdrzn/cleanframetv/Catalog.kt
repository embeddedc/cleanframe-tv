package io.github.mdrzn.cleanframetv

import org.json.JSONObject

/** One row of the web interface's app catalogue (see app/device.js). */
data class CatalogEntry(val appId: String, val name: String, val kind: String, val icon: String)

/** A launchable activity found by PackageManager. */
data class AppCandidate(val packageName: String, val label: String, val leanback: Boolean, val system: Boolean = false)

/** A TV input found by TvInputManager. */
data class InputCandidate(val inputId: String, val type: Int, val label: String, val parentId: String?)

/** An HDMI input as shown to the web interface, plus the real input id used to switch to it. */
data class InputEntry(val entry: CatalogEntry, val inputId: String)

/** An ADB configuration split into what the web interface stores and what the Android side keeps. */
data class Config(val web: String, val inputNames: Map<String, String>, val buttons: Map<Int, String>?)

/** Pure catalogue rules, kept free of Android types so they run as JVM unit tests. */
object Catalog {
    /** android.media.tv.TvInputInfo.TYPE_HDMI */
    const val TYPE_HDMI = 1007

    /**
     * One entry per package (the TV/leanback activity wins), without this launcher, sorted by name.
     * System apps with only a phone-style entry (vendor services, the desk clock) are left out.
     */
    fun apps(candidates: List<AppCandidate>, selfPackage: String): List<CatalogEntry> {
        val best = LinkedHashMap<String, AppCandidate>()
        for (candidate in candidates) {
            if (candidate.packageName == selfPackage) continue
            val previous = best[candidate.packageName]
            if (previous == null || (candidate.leanback && !previous.leanback)) best[candidate.packageName] = candidate
        }
        return best.values
            .filter { it.leanback || !it.system }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .map { CatalogEntry(it.packageName, it.label.trim().ifEmpty { it.packageName }, "app", "/icons/${it.packageName}.png") }
    }

    /** HDMI ports only (no tuners, AV, or per-device CEC children), with user-chosen names when set. */
    fun inputs(candidates: List<InputCandidate>, customNames: Map<String, String>): List<InputEntry> {
        val seen = HashSet<String>()
        return candidates
            .filter { it.type == TYPE_HDMI && it.parentId == null }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
            .mapNotNull { candidate ->
                val appId = inputAppId(candidate.inputId)
                if (!seen.add(appId)) return@mapNotNull null
                val name = customNames[appId]?.trim()?.takeIf { it.isNotEmpty() } ?: candidate.label.trim().ifEmpty { "HDMI" }
                InputEntry(CatalogEntry(appId, name, "input", ""), candidate.inputId)
            }
    }

    /** A stable id the web interface accepts (letters, digits, dot, dash, underscore). */
    fun inputAppId(inputId: String): String {
        val last = inputId.substringAfterLast('/').lowercase().replace(Regex("[^a-z0-9_-]"), "_").trim('_')
        return "input." + last.ifEmpty { "hdmi" }
    }

    fun toJson(entries: List<CatalogEntry>): String = entries.joinToString(",", "[", "]") {
        """{"appId":${quote(it.appId)},"name":${quote(it.name)},"kind":${quote(it.kind)},"icon":${quote(it.icon)}}"""
    }

    fun quote(value: String): String {
        val out = StringBuilder(value.length + 2).append('"')
        for (ch in value) when {
            ch == '"' -> out.append("\\\"")
            ch == '\\' -> out.append("\\\\")
            ch == '\n' -> out.append("\\n")
            ch == '\r' -> out.append("\\r")
            ch == '\t' -> out.append("\\t")
            ch < ' ' || ch == ' ' || ch == ' ' -> out.append(String.format("\\u%04x", ch.code))
            else -> out.append(ch)
        }
        return out.append('"').toString()
    }

    /**
     * Splits an ADB configuration (`--es config '<json>'`) into the part handed to the web interface,
     * the HDMI input names and the remote button targets (null when the config doesn't mention buttons).
     */
    fun splitConfig(json: String): Config {
        val obj = JSONObject(json)
        val names = LinkedHashMap<String, String>()
        obj.optJSONObject("inputNames")?.let { map -> map.keys().forEach { key -> names[key] = map.optString(key) } }
        val buttons = obj.optJSONObject("buttons")?.let { buttonsOf(it) }
        obj.remove("inputNames")
        obj.remove("buttons")
        return Config(obj.toString(), names, buttons)
    }

    /** Keycode → app or input id; keys must be Android keycodes as numbers, empty targets are dropped. */
    private fun buttonsOf(map: JSONObject): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        map.keys().forEach { key ->
            val code = key.toIntOrNull()
            val target = map.optString(key).trim()
            if (code != null && code > 0 && target.isNotEmpty()) out[code] = target
        }
        return out
    }

    fun buttonsJson(buttons: Map<Int, String>): String =
        buttons.entries.joinToString(",", "{", "}") { "${quote(it.key.toString())}:${quote(it.value)}" }

    fun parseButtons(json: String): Map<Int, String> =
        try { buttonsOf(JSONObject(json)) } catch (e: Exception) { emptyMap() }
}
