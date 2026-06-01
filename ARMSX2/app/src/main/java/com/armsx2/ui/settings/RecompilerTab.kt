package com.armsx2.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.armsx2.config.Settings
import com.armsx2.ui.InGameOverlay

/**
 * Recompiler section of the in-game settings overlay.
 *
 * Disables here drop the corresponding CPU/COP onto its interpreter
 * fallback. They mirror EmuCore/CPU/Recompiler keys (see Settings field
 * comments for the exact `<section>/<key>`). Toggling these on a running
 * VM swaps the dispatch pointer via VMManager::ApplySettings's
 * CpusChanged path; existing JIT block caches are flushed automatically,
 * but mid-frame switching is still inherently risky — interpreter speed
 * is much lower so most scenes will tank performance, and a few games
 * have known interpreter divergences. Treat this tab as a debug tool.
 */
@Composable
fun RecompilerTab(state: MutableState<Settings>) {
    val s = state.value
    val scroll = remember { ScrollState(0) }

    fun apply(updated: Settings) = InGameOverlay.saveSettings(updated)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scroll),
    ) {
        Text(
            "Disabling a recompiler drops to interpreter — debug only, expect a heavy slowdown.",
            color = Color(0xFFB0B0B0),
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // 4-wide toggle grid, matching the Playing-Now + Performance look.
        // Row 1 groups the four CPU/COP recompilers; Row 2 holds Fastmem
        // with three trailing spacers so cell widths stay uniform across
        // both rows.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleGridRow {
                ToggleBubble("EE (R5900)", s.recEE, modifier = Modifier.weight(1f)) {
                    apply(s.copy(recEE = it))
                }
                ToggleBubble("IOP (R3000)", s.recIOP, modifier = Modifier.weight(1f)) {
                    apply(s.copy(recIOP = it))
                }
                ToggleBubble("VU0", s.recVU0, modifier = Modifier.weight(1f)) {
                    apply(s.copy(recVU0 = it))
                }
                ToggleBubble("VU1", s.recVU1, modifier = Modifier.weight(1f)) {
                    apply(s.copy(recVU1 = it))
                }
            }
            BubbleGridRow {
                ToggleBubble("Fastmem", s.enableFastmem, modifier = Modifier.weight(1f)) {
                    apply(s.copy(enableFastmem = it))
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
