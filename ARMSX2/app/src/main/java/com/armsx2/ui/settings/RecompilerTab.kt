package com.armsx2.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
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
import com.armsx2.config.ConfigStore
import com.armsx2.config.Settings

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

    fun apply(updated: Settings) {
        state.value = updated
        ConfigStore.saveGlobal(updated)
        updated.applyTo()
    }

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
        ToggleRow("EE Recompiler (R5900)", s.recEE) {
            apply(s.copy(recEE = it))
        }
        SettingsDivider()
        ToggleRow("IOP Recompiler (R3000)", s.recIOP) {
            apply(s.copy(recIOP = it))
        }
        SettingsDivider()
        ToggleRow("VU0 Recompiler", s.recVU0) {
            apply(s.copy(recVU0 = it))
        }
        SettingsDivider()
        ToggleRow("VU1 Recompiler", s.recVU1) {
            apply(s.copy(recVU1 = it))
        }
        SettingsDivider()
        ToggleRow("Fastmem (page-fault backpatch)", s.enableFastmem) {
            apply(s.copy(enableFastmem = it))
        }
    }
}
