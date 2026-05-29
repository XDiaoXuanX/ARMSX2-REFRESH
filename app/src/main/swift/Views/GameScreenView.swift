// GameScreenView.swift — Unified game screen (Metal + Virtual Pad + Menu)
// SPDX-License-Identifier: GPL-3.0+

import SwiftUI
import UIKit

struct GameScreenView: View {
    @State private var appState = AppState.shared
    @State private var settings = SettingsStore.shared
    @State private var padVisible = true
    @State private var fullScreen = false
    @State private var showSaveStates = false
    @State private var saveStateStatus: String? = nil
    @State private var saveStateGameReady = false
    @State private var saveStateMenuVersion = 0

    var body: some View {
        GeometryReader { geo in
            let isLandscape = geo.size.width > geo.size.height

            if isLandscape {
                // Landscape: always use full screen area for Metal + pad
                // so that pad coordinates match the layout editor exactly.
                ZStack {
                    MetalGameView()
                    if padVisible {
                        VirtualControllerView(isLandscape: true)
                    }
                    menuOverlay(isLandscape: true)
                }
                .ignoresSafeArea()  // Always fullscreen layout in landscape
            } else {
                // Portrait: Metal top half, pad bottom half, within safe area
                VStack(spacing: 0) {
                    MetalGameView()
                        .frame(height: geo.size.height / 2)
                    if padVisible {
                        VirtualControllerView()
                            .frame(height: geo.size.height / 2)
                    } else {
                        Spacer()
                    }
                }
                .overlay(alignment: .topTrailing) {
                    menuOverlay(isLandscape: false)
                }
            }
        }
        .onChange(of: fullScreen) { _, newValue in
            ARMSX2Bridge.setFullScreen(newValue)
        }
        .sheet(isPresented: $showSaveStates) {
            SaveStatesPanel { message in
                presentSaveStateStatus(message)
            }
        }
        .overlay(alignment: .bottom) {
            saveStateToast
        }
        .onAppear {
            refreshSaveStateMenuAvailability(reason: "appear")
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                refreshSaveStateMenuAvailability(reason: "appear_delay_250ms")
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) {
                refreshSaveStateMenuAvailability(reason: "appear_delay_1s")
            }
        }
        .onChange(of: appState.runningGameName) { _, _ in
            refreshSaveStateMenuAvailability(reason: "running_game_changed")
        }
    }

    private func menuOverlay(isLandscape: Bool) -> some View {
        VStack {
            HStack {
                Spacer()
                menuButton(isLandscape: isLandscape)
            }
            .padding(.top, isLandscape ? 8 : 4)
            .padding(.trailing, isLandscape ? 8 : 4)
            Spacer()
        }
    }

    private func menuButton(isLandscape: Bool) -> some View {
        Menu {
            Toggle(isOn: Binding(
                get: { settings.osdPreset != .off },
                set: { newValue in
                    if newValue {
                        settings.osdPreset = .simple
                        ARMSX2Bridge.setPerformanceOverlayVisible(true)
                    } else {
                        settings.osdPreset = .off
                        ARMSX2Bridge.setPerformanceOverlayVisible(false)
                    }
                }
            )) {
                Label("OSD", systemImage: "speedometer")
            }
            Toggle(isOn: $padVisible) {
                Label("Virtual Pad", systemImage: "gamecontroller")
            }
            if isLandscape {
                Toggle(isOn: $fullScreen) {
                    Label("Full Screen", systemImage: "arrow.up.left.and.arrow.down.right")
                }
            }
            if shouldOfferSaveStates {
                Button {
                    refreshSaveStateMenuAvailability(reason: "menu_save_states_selected")
                    saveStateUILog("@@SAVE_STATE_UI@@ menu_open offer=\(shouldOfferSaveStates ? 1 : 0) valid=\(ARMSX2Bridge.hasValidSaveStateGame() ? 1 : 0) game=\(appState.runningGameName ?? "(nil)")")
                    showSaveStates = true
                } label: {
                    Label("Save States", systemImage: "square.stack.3d.up.fill")
                }
            }
            Divider()
            Button {
                appState.returnToMenu()
            } label: {
                Label("Back to Menu", systemImage: "list.bullet")
            }
        } label: {
            Image(systemName: "ellipsis.circle.fill")
                .font(.title3)
                .foregroundStyle(.white.opacity(0.5))
                .padding(6)
                .background(.black.opacity(0.15), in: Circle())
        }
        .id(saveStateMenuVersion)
        .simultaneousGesture(TapGesture().onEnded {
            refreshSaveStateMenuAvailability(reason: "menu_tap")
        })
    }

    private var isRealGameSession: Bool {
        guard let runningGameName = appState.runningGameName else {
            return false
        }
        return runningGameName != "BIOS"
    }

    private var shouldOfferSaveStates: Bool {
        isRealGameSession || saveStateGameReady
    }

    private func refreshSaveStateMenuAvailability(reason: String) {
        let valid = ARMSX2Bridge.hasValidSaveStateGame()
        let ready = isRealGameSession || valid
        saveStateUILog("@@SAVE_STATE_UI@@ availability reason=\(reason) real_game=\(isRealGameSession ? 1 : 0) valid=\(valid ? 1 : 0) ready=\(ready ? 1 : 0) current=\(saveStateGameReady ? 1 : 0) game=\(appState.runningGameName ?? "(nil)")")
        if saveStateGameReady != ready {
            saveStateGameReady = ready
            saveStateMenuVersion += 1
        }
    }

    @ViewBuilder
    private var saveStateToast: some View {
        if let saveStateStatus {
            Text(saveStateStatus)
                .font(.callout.weight(.semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 10)
                .background(.black.opacity(0.72), in: Capsule())
                .padding(.bottom, 24)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
        }
    }

    private func presentSaveStateStatus(_ message: String) {
        withAnimation(.easeOut(duration: 0.18)) {
            saveStateStatus = message
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.2) {
            guard saveStateStatus == message else { return }
            withAnimation(.easeIn(duration: 0.18)) {
                saveStateStatus = nil
            }
        }
    }
}

private struct SaveStatesPanel: View {
    @Environment(\.dismiss) private var dismiss
    @State private var slots: [ARMSX2SaveStateSlotInfo] = []
    @State private var busySlot: Int? = nil
    @State private var pendingOverwrite: ARMSX2SaveStateSlotInfo? = nil

    let statusHandler: (String) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 10) {
                    ForEach(slots, id: \.slot) { slot in
                        SaveStateSlotRow(
                            info: slot,
                            isBusy: busySlot == slot.slot,
                            onSave: { save(slot) },
                            onLoad: { load(slot) },
                            onOverwrite: { pendingOverwrite = slot }
                        )
                    }
                }
                .padding()
            }
            .navigationTitle("Save States")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .onAppear(perform: refresh)
            .confirmationDialog(
                "Overwrite Slot \(pendingOverwrite?.slot ?? 0)?",
                isPresented: Binding(
                    get: { pendingOverwrite != nil },
                    set: { newValue in
                        if !newValue {
                            pendingOverwrite = nil
                        }
                    }
                ),
                titleVisibility: .visible
            ) {
                Button("Overwrite", role: .destructive) {
                    if let pendingOverwrite {
                        save(pendingOverwrite)
                    }
                    pendingOverwrite = nil
                }
                Button("Cancel", role: .cancel) {
                    pendingOverwrite = nil
                }
            }
        }
    }

    private func refresh() {
        slots = ARMSX2Bridge.saveStateSlots()
        let occupied = slots.filter(\.occupied).count
        saveStateUILog("@@SAVE_STATE_UI@@ refresh slots=\(slots.count) occupied=\(occupied)")
    }

    private func save(_ slot: ARMSX2SaveStateSlotInfo) {
        saveStateUILog("@@SAVE_STATE_UI@@ save_begin slot=\(slot.slot) occupied=\(slot.occupied)")
        busySlot = slot.slot
        ARMSX2Bridge.saveState(toSlot: slot.slot) { success in
            busySlot = nil
            refresh()
            saveStateUILog("@@SAVE_STATE_UI@@ save_end slot=\(slot.slot) success=\(success ? 1 : 0)")
            statusHandler(success ? "State saved to slot \(slot.slot)" : "Failed to save slot \(slot.slot)")
        }
    }

    private func load(_ slot: ARMSX2SaveStateSlotInfo) {
        saveStateUILog("@@SAVE_STATE_UI@@ load_begin slot=\(slot.slot) occupied=\(slot.occupied)")
        busySlot = slot.slot
        ARMSX2Bridge.loadState(fromSlot: slot.slot) { success in
            busySlot = nil
            refresh()
            saveStateUILog("@@SAVE_STATE_UI@@ load_end slot=\(slot.slot) success=\(success ? 1 : 0)")
            statusHandler(success ? "State loaded from slot \(slot.slot)" : "Failed to load slot \(slot.slot)")
            if success {
                dismiss()
            }
        }
    }
}

private func saveStateUILog(_ message: String) {
    ARMSX2Bridge.logSaveStateEvent(message)
}

private struct SaveStateSlotRow: View {
    let info: ARMSX2SaveStateSlotInfo
    let isBusy: Bool
    let onSave: () -> Void
    let onLoad: () -> Void
    let onOverwrite: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            SaveStatePreview(data: info.previewPNGData, occupied: info.occupied)
                .frame(width: 96, height: 72)

            VStack(alignment: .leading, spacing: 4) {
                Text("Slot \(info.slot)")
                    .font(.headline)

                if info.occupied {
                    if let modifiedDate = info.modifiedDate {
                        Text(Self.dateFormatter.string(from: modifiedDate))
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Text(info.fileName)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                } else {
                    Text("Empty")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer(minLength: 8)

            if isBusy {
                ProgressView()
                    .frame(width: 88)
            } else if info.occupied {
                HStack(spacing: 8) {
                    Button(action: onLoad) {
                        Label("Load", systemImage: "arrow.down.circle")
                    }
                    .buttonStyle(.borderedProminent)

                    Button(action: onOverwrite) {
                        Label("Overwrite", systemImage: "arrow.triangle.2.circlepath")
                    }
                    .buttonStyle(.bordered)
                }
            } else {
                Button(action: onSave) {
                    Label("Save", systemImage: "square.and.arrow.down")
                }
                .buttonStyle(.borderedProminent)
            }
        }
        .padding(12)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter
    }()
}

private struct SaveStatePreview: View {
    let data: Data?
    let occupied: Bool

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .fill(.black.opacity(0.12))

            if let data, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
            } else {
                Image(systemName: occupied ? "photo" : "tray")
                    .font(.title2)
                    .foregroundStyle(.secondary)
            }
        }
        .clipped()
    }
}
