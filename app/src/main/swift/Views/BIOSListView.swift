// BIOSListView.swift — BIOS file list with default selection
// SPDX-License-Identifier: GPL-3.0+

import SwiftUI
import UniformTypeIdentifiers

struct BIOSListView: View {
    @State private var bioses: [ARMSX2BIOSInfo] = []
    @State private var defaultBIOS: String = ""
    @State private var fileImporter = FileImportHandler.shared
    @State private var showBIOSImporter = false

    var body: some View {
        NavigationStack {
            Group {
                if bioses.isEmpty {
                    emptyState
                } else {
                    List {
                        ForEach(bioses, id: \.self) { bios in
                            biosRow(bios)
                        }
                    }
#if targetEnvironment(macCatalyst)
                    .listStyle(.inset)
#endif
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("BIOS")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showBIOSImporter = true } label: {
                        Image(systemName: "plus")
                    }
                    .accessibilityLabel("Import BIOS")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { loadBIOSes() } label: {
                        Image(systemName: "arrow.clockwise")
                    }
                }
            }
            .alert("Import Result", isPresented: $fileImporter.showImportAlert) {
                Button("OK") {}
            } message: {
                Text(fileImporter.lastImportMessage ?? "")
            }
            .sheet(isPresented: $showBIOSImporter) {
                ImportDocumentPicker(
                    allowedContentTypes: FileImportHandler.biosContentTypes,
                    allowsMultipleSelection: true
                ) { result in
                    showBIOSImporter = false
                    switch result {
                    case .success(let urls):
                        NSLog("[ARMSX2 iOS BIOS] picker completed with %d URL(s)", urls.count)
                        fileImporter.handleURLs(urls, preferredDestination: .bios)
                        loadBIOSes()
                        if defaultBIOS.isEmpty, let firstBIOS = bioses.first?.fileName {
                            ARMSX2Bridge.setDefaultBIOS(firstBIOS)
                            defaultBIOS = firstBIOS
                        }
                        if bioses.isEmpty, !urls.isEmpty {
                            fileImporter.lastImportMessage = [
                                fileImporter.lastImportMessage,
                                "No usable PS2 BIOS was found after import. Use a 1-50 MB .bin or .rom BIOS dump."
                            ]
                            .compactMap { $0 }
                            .joined(separator: "\n")
                            fileImporter.showImportAlert = true
                        }
                    case .failure(let error):
                        if (error as NSError).code != NSUserCancelledError {
                            fileImporter.lastImportMessage = "Import failed: \(error.localizedDescription)"
                            fileImporter.showImportAlert = true
                        }
                    }
                }
            }
        }
        .onAppear { loadBIOSes() }
    }

    private func biosRow(_ bios: ARMSX2BIOSInfo) -> some View {
        Button {
            ARMSX2Bridge.setDefaultBIOS(bios.fileName)
            defaultBIOS = bios.fileName
        } label: {
            HStack(spacing: 12) {
                regionBadge(for: bios)

                VStack(alignment: .leading, spacing: 4) {
                    Text(bios.fileName)
                        .font(.body)
                        .foregroundStyle(.primary)
                    Text(bios.valid ? "\(bios.regionName) BIOS" : "Unknown BIOS Region")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if bios.valid && !bios.descriptionText.isEmpty {
                        Text(bios.descriptionText)
                            .font(.caption2.monospaced())
                            .foregroundStyle(.tertiary)
                            .lineLimit(1)
                    }
                }
                Spacer()
                if bios.fileName == defaultBIOS {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.blue)
                }
            }
        }
        .foregroundStyle(.primary)
    }

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "cpu")
                .font(.system(size: 48))
                .foregroundStyle(.secondary)
            Text("No BIOS Found")
                .font(.title2)
                .fontWeight(.semibold)
            Text("Import a PS2 BIOS dump to enable booting.")
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
            Button {
                showBIOSImporter = true
            } label: {
                Label("Import BIOS", systemImage: "plus")
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func loadBIOSes() {
        bioses = ARMSX2Bridge.availableBIOSInfos()
        defaultBIOS = ARMSX2Bridge.defaultBIOSName()
    }

    private func regionBadge(for bios: ARMSX2BIOSInfo) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(Color(.secondarySystemGroupedBackground))
                .frame(width: 44, height: 44)

            if let flag = flagEmoji(for: bios.countryCode) {
                Text(flag)
                    .font(.title2)
            } else {
                Image(systemName: "globe")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityLabel(bios.valid ? "\(bios.regionName) BIOS" : "Unknown BIOS region")
    }

    private func flagEmoji(for countryCode: String) -> String? {
        let scalars = countryCode.uppercased().unicodeScalars
        guard scalars.count == 2 else { return nil }

        var unicodeScalars = String.UnicodeScalarView()
        for scalar in scalars {
            guard scalar.value >= 65, scalar.value <= 90,
                  let regional = UnicodeScalar(0x1F1E6 + scalar.value - 65) else {
                return nil
            }
            unicodeScalars.append(regional)
        }

        return String(unicodeScalars)
    }
}
