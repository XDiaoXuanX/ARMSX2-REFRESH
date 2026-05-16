package com.armsx2.ui.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.armsx2.config.ConfigStore
import com.armsx2.config.Settings

/**
 * Performance section of the in-game settings overlay.
 *
 * Mutates the global [Settings] in [ConfigStore] on every change and
 * applies via [Settings.applyTo] — toggles take effect immediately on a
 * running VM. Every visible setting maps 1-1 onto an EmuCore key (see
 * Settings field comments for the exact `<section>/<key>`).
 *
 * Column + verticalScroll instead of LazyColumn so the tab can sit
 * inside the wrap-content RootTabs container without needing a hard
 * height bound. List is short (~9 rows) so non-lazy is fine.
 */
@Composable
fun PerformanceTab(state: MutableState<Settings>) {
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
        IntSliderRow(
            label = "EE Cycle Rate",
            value = s.eeCycleRate,
            min = -3,
            max = 3,
            valueFormatter = { rate ->
                when (rate) {
                    -3 -> "50%"
                    -2 -> "60%"
                    -1 -> "75%"
                    0 -> "100%"
                    1 -> "130%"
                    2 -> "180%"
                    3 -> "300%"
                    else -> "$rate"
                }
            },
            onChange = { apply(s.copy(eeCycleRate = it)) },
        )
        SettingsDivider()
        IntSliderRow(
            label = "EE Cycle Skip",
            value = s.eeCycleSkip,
            min = 0,
            max = 3,
            onChange = { apply(s.copy(eeCycleSkip = it)) },
        )
        SettingsDivider()
        Spacer(Modifier.height(8.dp))
        // On/Off toggles as a 4-wide bubble grid, matching the Playing-Now
        // action grid in InGameOverlay. Two rows of four cells — last cell
        // is a Spacer so the seven toggles keep uniform widths across both
        // rows. Labels are abbreviated to fit the ~74dp cell.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BubbleGridRow {
                ToggleBubble("MTVU", s.mtvu, modifier = Modifier.weight(1f)) {
                    apply(s.copy(mtvu = it))
                }
                ToggleBubble("Instant VU1", s.vu1Instant, modifier = Modifier.weight(1f)) {
                    apply(s.copy(vu1Instant = it))
                }
                ToggleBubble("VU Flag Hack", s.vuFlagHack, modifier = Modifier.weight(1f)) {
                    apply(s.copy(vuFlagHack = it))
                }
                ToggleBubble("Fast CDVD", s.fastCDVD, modifier = Modifier.weight(1f)) {
                    apply(s.copy(fastCDVD = it))
                }
            }
            BubbleGridRow {
                ToggleBubble("INTC Stat", s.intcStat, modifier = Modifier.weight(1f)) {
                    apply(s.copy(intcStat = it))
                }
                ToggleBubble("Wait Loop", s.waitLoop, modifier = Modifier.weight(1f)) {
                    apply(s.copy(waitLoop = it))
                }
                ToggleBubble("Frame Limiter", s.frameLimitEnable, modifier = Modifier.weight(1f)) {
                    apply(s.copy(frameLimitEnable = it))
                }
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
