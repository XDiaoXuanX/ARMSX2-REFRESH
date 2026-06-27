package com.armsx2

import android.net.Uri
import androidx.compose.runtime.mutableStateOf

/**
 * Box-art style for the library: 2D flat scans (the "default" mirror, JPG) or
 * 3D rendered cases (the "3d" mirror, PNG). Both come from the same xlenore
 * repos. Persisted in Main.prefs and read by [GameInfo.coverUrl], so flipping
 * it recomposes the grid and re-downloads covers in the chosen style.
 */
object CoverArtStyle {
    private const val KEY = "library.coverArt3d"
    val use3d = mutableStateOf(false)
    fun load() { use3d.value = Main.prefs.getBoolean(KEY, false) }
    fun set(value: Boolean) {
        use3d.value = value
        Main.prefs.edit().putBoolean(KEY, value).apply()
    }
}

/**
 * Library toggle: show the game title under each cover on the shelves. Off by
 * default — the cover already carries the title and a label under every card
 * crowds the shelf UI — but exposed as a quick toggle on the library's left
 * rail for users who keep multiple versions of a game or browse by name.
 */
object LibraryTitles {
    private const val KEY = "library.showTitles"
    val show = mutableStateOf(false)
    fun load() { show.value = Main.prefs.getBoolean(KEY, false) }
    fun set(value: Boolean) {
        show.value = value
        Main.prefs.edit().putBoolean(KEY, value).apply()
    }
}

/**
 * One row in the games-list screen. Today the title/serial come from
 * filename parsing — game titles like "Final Fantasy X (USA) [SLUS-20312]"
 * are common dump conventions. compatibility is left at 0 (no stars filled)
 * until we add a gamedb JNI bridge.
 *
 * `platform` distinguishes PS1 ("ps1") from PS2 ("ps2") so we hit the
 * right cover repo: xlenore/ps2-covers vs xlenore/psx-covers. Native
 * (getGameSerialFromFd) tags its return with the platform when SYSTEM.CNF
 * is parseable; filename-only fallback defaults to "ps2".
 */
enum class GamePlatform(val key: String) {
    PS2("ps2"),
    PS1("ps1");

    companion object {
        fun fromKey(s: String?): GamePlatform =
            if (s == "ps1") PS1 else PS2
    }
}

data class GameInfo(
    val uri: Uri,
    val title: String,
    val serial: String?,
    val compatibility: Int = 0,    // 0..5 (TODO: pull from gamedb)
    val extension: String = "",    // upper-case container ext, e.g. "ISO", "CHD"
    val platform: GamePlatform = GamePlatform.PS2,
) {
    val coverUrl: String? get() = serial?.let { s ->
        val repo = when (platform) {
            GamePlatform.PS2 -> "ps2-covers"
            GamePlatform.PS1 -> "psx-covers"
        }
        // 3D cases live under covers/3d/*.png; flat 2D scans under
        // covers/default/*.jpg. Coil decodes by content, so the extension
        // mismatch on the cached file is fine.
        if (CoverArtStyle.use3d.value)
            "https://raw.githubusercontent.com/xlenore/$repo/main/covers/3d/$s.png"
        else
            "https://raw.githubusercontent.com/xlenore/$repo/main/covers/default/$s.jpg"
    }

    /** Human-readable region (USA / Europe / Japan / …) from the serial prefix,
     *  or null if unrecognized. Shown under the cover so users can tell apart
     *  multiple regional versions of the same game. */
    val region: String? get() = serial?.let { regionForSerial(it) }

    /** Region as a flag emoji (🇺🇸 / 🇪🇺 / 🇯🇵 / …) for the cover label, or null.
     *  Rendered ahead of the title so the region is always visible even when a
     *  long name wraps/ellipsizes. */
    val regionFlag: String? get() = region?.let { regionFlagFor(it) }

    /** A short version/edition tag shown under the title so two copies of the
     *  same game can be told apart: a disc-version token parsed from the dump
     *  filename (e.g. "v3.00") when present, otherwise the serial. */
    val versionTag: String? get() {
        val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':')
        return name?.let { FilenameParser.versionTokenOf(it) } ?: serial
    }
}

/** Map a PS1/PS2 serial prefix to a region label. */
fun regionForSerial(serial: String): String? = when (serial.take(4).uppercase()) {
    "SLUS", "SCUS", "PBPX", "LSP0" -> "USA"
    "SLES", "SCES", "SLED", "SCED", "SLPN" -> "Europe"
    "SLPS", "SLPM", "SCPS", "SCAJ", "ALCH", "PAPX", "ROSE", "TCPS", "KOEI", "PCPX", "CPCS" -> "Japan"
    "SLKA", "SCKA" -> "Korea"
    "SLAJ" -> "Asia"
    else -> null
}

/** Map a region label to a flag emoji. Asia falls back to a globe. */
fun regionFlagFor(region: String): String? = when (region) {
    "USA" -> "🇺🇸"
    "Europe" -> "🇪🇺"
    "Japan" -> "🇯🇵"
    "Korea" -> "🇰🇷"
    "Asia" -> "🌏"
    else -> null
}

/**
 * Best-effort serial extractor. Recognized dump conventions:
 *   "Game (USA) [SLUS-20312].iso"      → SLUS-20312
 *   "Game (USA) [SLUS_203.12].iso"     → SLUS-20312
 *   "SCUS_972.28 - Game.iso"           → SCUS-97228
 *   "slus_203.12.iso"                  → SLUS-20312
 *
 * The pattern matches 4 letters + optional separator + 3 digits + optional
 * dot + 2 digits, normalized to "AAAA-NNNNN" upper-case.
 */
object FilenameParser {
    private val serialRegex = Regex("""([A-Za-z]{4})[\s_-]?(\d{3})\.?(\d{2})""")
    private val tagsRegex = Regex("""[\[(].*?[\])]""")
    // Disc-version token (e.g. "v3.00", "v 1.0"). The 'v' prefix is required so
    // release years ("(2004)") and unrelated x.y numbers aren't mistaken for it.
    private val versionRegex = Regex("""(?i)\bv\.?\s?(\d{1,2}(?:\.\d{1,2}){1,2})\b""")

    /** A disc-version token like "v3.00" parsed from a filename, or null. Lets
     *  two copies of the same game (same serial, different disc revision) be told
     *  apart when the dump filename carries the version. */
    fun versionTokenOf(filename: String): String? =
        versionRegex.find(filename)?.let { "v" + it.groupValues[1] }
    private val whitespaceRegex = Regex("""\s+""")
    private val nonWordRegex = Regex("""[^a-z0-9]+""")

    private data class FilenameAlias(val title: String, val serial: String)

    private fun aliasFor(filenameWithoutExt: String): FilenameAlias? {
        val normalized = filenameWithoutExt
            .lowercase()
            .replace(nonWordRegex, " ")
            .trim()

        // Some PAL DMC2 CHDs are named by disc character rather than serial,
        // and their raw-CD layout can be awkward to probe. Keep this narrow so
        // broader filename-only games still rely on explicit serial tokens.
        if (!normalized.contains("devil may cry 2"))
            return null

        return when {
            normalized.contains("dante") ->
                FilenameAlias("Devil May Cry 2 [Dante Disc]", "SLES-82011")
            normalized.contains("lucia") ->
                FilenameAlias("Devil May Cry 2 [Lucia Disc]", "SLES-82012")
            else -> null
        }
    }

    fun parse(filename: String): Pair<String, String?> {
        val withoutExt = filename.substringBeforeLast('.')
        val match = serialRegex.find(withoutExt)
        val serial = match?.let {
            "${it.groupValues[1].uppercase()}-${it.groupValues[2]}${it.groupValues[3]}"
        }
        if (serial == null) {
            aliasFor(withoutExt)?.let { return it.title to it.serial }
        }
        // Strip the matched serial token + any [region] / (lang) tags so the
        // displayed title is the game name rather than the full filename.
        var title = withoutExt
        if (match != null) title = title.replace(match.value, "")
        title = title.replace(tagsRegex, "")
            .replace(whitespaceRegex, " ")
            .trim(' ', '-', '_', '.')
        if (title.isEmpty()) title = withoutExt
        return title to serial
    }
}
