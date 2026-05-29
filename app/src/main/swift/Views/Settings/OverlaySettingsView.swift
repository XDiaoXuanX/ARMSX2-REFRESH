// OverlaySettingsView.swift — OSD preset selector
// SPDX-License-Identifier: GPL-3.0+

import SwiftUI

struct OverlaySettingsView: View {
    @State private var settings = SettingsStore.shared

    var body: some View {
        Form {
            Section("Performance Overlay") {
                Picker("Preset", selection: $settings.osdPreset) {
                    ForEach(OsdPreset.allCases, id: \.self) { preset in
                        Text(preset.label).tag(preset)
                    }
                }
                .pickerStyle(.segmented)

                Picker("Position", selection: $settings.osdPerformancePosition) {
                    Text("Hidden").tag(0)
                    Text("Top Left").tag(1)
                    Text("Top Right").tag(2)
                }
            }

            Section("Displayed Items") {
                Toggle("Show FPS", isOn: $settings.osdShowFPS)
                Toggle("Show VPS", isOn: $settings.osdShowVPS)
                Toggle("Show Speed", isOn: $settings.osdShowSpeed)
                Toggle("Show CPU", isOn: $settings.osdShowCPU)
                Toggle("Show GPU", isOn: $settings.osdShowGPU)
                Toggle("Show Resolution", isOn: $settings.osdShowResolution)
                Toggle("Show GS Stats", isOn: $settings.osdShowGSStats)
                Toggle("Show Indicators", isOn: $settings.osdShowIndicators)
                Toggle("Show Settings", isOn: $settings.osdShowSettings)
                Toggle("Show Inputs", isOn: $settings.osdShowInputs)
                Toggle("Show Frame Times", isOn: $settings.osdShowFrameTimes)
                Toggle("Show Version", isOn: $settings.osdShowVersion)
                Toggle("Show Hardware Info", isOn: $settings.osdShowHardwareInfo)
            }

            Section("Notes") {
                Text("These match ARMSX2 Android's OSD/stat controls where practical. When Show Version is enabled, the overlay label displays ARMSX2 iOS.")
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Overlay")
        .navigationBarTitleDisplayMode(.inline)
    }
}
