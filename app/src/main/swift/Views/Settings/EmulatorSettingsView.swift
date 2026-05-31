// EmulatorSettingsView.swift — EE/IOP/VU/boot/speedhack settings
// SPDX-License-Identifier: GPL-3.0+

import SwiftUI

struct EmulatorSettingsView: View {
    @State private var settings = SettingsStore.shared

    var body: some View {
        Form {
            Section {
                Toggle(isOn: Binding(
                    get: { settings.eeCoreType != 1 },
                    set: { settings.eeCoreType = $0 ? 2 : 1 }
                )) {
                    HStack {
                        Text("EE Core")
                        Spacer()
                        Text(settings.eeCoreType != 1 ? "ARM64 JIT" : "Interpreter")
                            .foregroundStyle(.secondary)
                            .font(.callout)
                    }
                }
                Toggle(isOn: $settings.iopRecompiler) {
                    HStack {
                        Text("IOP")
                        Spacer()
                        Text(settings.iopRecompiler ? "JIT" : "Interpreter")
                            .foregroundStyle(.secondary)
                            .font(.callout)
                    }
                }
                Toggle(isOn: $settings.vu0Recompiler) {
                    HStack {
                        Text("VU0")
                        Spacer()
                        Text(settings.vu0Recompiler ? "JIT" : "Interpreter")
                            .foregroundStyle(.secondary)
                            .font(.callout)
                    }
                }
                Toggle(isOn: $settings.vu1Recompiler) {
                    HStack {
                        Text("VU1")
                        Spacer()
                        Text(settings.vu1Recompiler ? "JIT" : "Interpreter")
                            .foregroundStyle(.secondary)
                            .font(.callout)
                    }
                }
                Text("Changes take effect on next VM boot.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } header: {
                Text("CPU Recompiler")
            }

            Section("Boot") {
                Toggle("Fast Boot", isOn: $settings.fastBoot)
                Text("Skips BIOS intro. Some games require this OFF.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Memory") {
                Toggle("Fastmem", isOn: $settings.fastmem)
                Text("Direct memory mapping for EE. Disable if 3D graphics are broken. Requires restart.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section("Performance") {
                Toggle("Frame Limiter", isOn: $settings.frameLimiterEnabled)

                if settings.frameLimiterEnabled {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("FPS Target")
                            Spacer()
                            Text(Self.formatFPS(settings.targetFPS))
                                .foregroundStyle(.secondary)
                                .font(.callout.monospacedDigit())
                        }

                        Slider(
                            value: $settings.targetFPS,
                            in: SettingsStore.minTargetFPS...SettingsStore.maxTargetFPS,
                            step: 1.0
                        )

                        HStack {
                            Text(Self.formatFPS(SettingsStore.minTargetFPS))
                            Spacer()
                            Button("60 FPS") {
                                settings.targetFPS = SettingsStore.defaultTargetFPS
                            }
                            .buttonStyle(.borderless)
                            Spacer()
                            Text(Self.formatFPS(SettingsStore.maxTargetFPS))
                        }
                        .font(.caption.monospacedDigit())
                        .foregroundStyle(.secondary)
                    }
                } else {
                    HStack {
                        Text("Speed Target")
                        Spacer()
                        Text("Unlocked")
                            .foregroundStyle(.orange)
                            .font(.callout.monospacedDigit())
                    }
                }

                HStack {
                    Text("NTSC Base Rate")
                    Spacer()
                    Text(Self.formatFPS(settings.ntscFramerate))
                        .foregroundStyle(.secondary)
                        .font(.callout.monospacedDigit())
                }

                HStack {
                    Text("PAL Base Rate")
                    Spacer()
                    Text(Self.formatFPS(settings.palFramerate))
                        .foregroundStyle(.secondary)
                        .font(.callout.monospacedDigit())
                }

                Text("FPS Target maps to PCSX2 Normal Speed: 60 FPS is normal NTSC timing, 30 FPS is about 50% speed, and higher values fast-forward. Turning the limiter OFF unlocks speed and can increase heat and battery drain.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section {
                Button("Use VU1 Interpreter Preset") {
                    settings.applyVU1CompatibilityPreset()
                }
                Button("Use Full Interpreter Preset") {
                    settings.applyFullInterpreterPreset()
                }
                Text("Use the VU1 preset first for boot crashes or VU1-related texture/rendering glitches. Full Interpreter is much slower, but helps isolate dynarec/JIT issues.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } header: {
                Text("Compatibility")
            } footer: {
                Text("Changes take effect on next VM boot.")
            }

            Section("Patches & Cheats") {
                Toggle("GameDB Automatic Fixes", isOn: Binding(
                    get: { settings.enableGameFixes && settings.enableGameDBHardwareFixes },
                    set: { enabled in
                        settings.enableGameFixes = enabled
                        settings.enableGameDBHardwareFixes = enabled
                    }
                ))
                Toggle("GameDB Core Fixes", isOn: $settings.enableGameFixes)
                Toggle("GameDB Graphics Fixes", isOn: $settings.enableGameDBHardwareFixes)
                Toggle("GameDB PNACH Patches", isOn: $settings.enablePatches)
                Toggle("Enable PNACH Cheats", isOn: $settings.enableCheats)
                Toggle("Widescreen Patches", isOn: $settings.enableWidescreenPatches)
                Toggle("No-Interlacing Patches", isOn: $settings.enableNoInterlacingPatches)

                Text("GameDB Core Fixes covers timing, clamps, and gamefixes. GameDB Graphics Fixes covers renderer-specific hardware fixes; turn it off globally or per-game if a title looks worse on Metal. PNACH cheats and 60 FPS patches can be imported from the in-game quick menu or from a game's long-press menu.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

            Section {
                Stepper("EE Cycle Rate: \(settings.eeCycleRate)", value: $settings.eeCycleRate, in: -3...3)
                Text("0 = Default. Negative = underclock (stable). Positive = overclock (fast but risky).")
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Toggle("Fast CDVD", isOn: $settings.fastCDVD)
                Toggle("VU1 Instant", isOn: $settings.vu1Instant)
                Toggle("MTVU", isOn: $settings.mtvu)
                Toggle("Wait Loop Detection", isOn: $settings.waitLoop)
                Toggle("INTC Stat Hack", isOn: $settings.intcStat)

                Text("VU1 Instant and MTVU are independent now. MTVU can help some games, but keep it off unless a game specifically benefits on iOS.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } header: {
                Text("Speedhacks")
            } footer: {
                Text("Changes take effect on next VM boot.")
            }

            Section {
                Button("Reset Emulator to Defaults") {
                    settings.resetEmulatorDefaults()
                }
                .foregroundStyle(.red)
            }
        }
        .navigationTitle("Emulator")
        .navigationBarTitleDisplayMode(.inline)
    }

    private static func formatFPS(_ value: Float) -> String {
        String(format: "%.2f FPS", value)
    }
}
