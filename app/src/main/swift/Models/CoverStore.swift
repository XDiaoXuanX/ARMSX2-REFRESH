// CoverStore.swift - Local game cover lookup/import for the iOS game library
// SPDX-License-Identifier: GPL-3.0+

import Foundation
import SwiftUI
import UniformTypeIdentifiers

@Observable
final class CoverStore: @unchecked Sendable {
    static let shared = CoverStore()

    static let imageExtensions: [String] = ["jpg", "jpeg", "png", "webp", "heic", "heif"]
    static let coverContentTypes: [UTType] = {
        var types: [UTType] = [.image]
        for ext in imageExtensions {
            if let type = UTType(filenameExtension: ext), !types.contains(type) {
                types.append(type)
            }
        }
        return types
    }()

    private let fileManager = FileManager.default

    var lastCoverMessage: String?
    var showCoverAlert = false

    private init() {}

    var primaryCoverDirectory: URL {
        let docs = URL(fileURLWithPath: ARMSX2Bridge.documentsDirectory(), isDirectory: true)
        let dir = docs.appendingPathComponent("armsx2_covers", isDirectory: true)
        try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    func coverURL(forGameName gameName: String, gamePath: URL?) -> URL? {
        let candidates = coverBaseCandidates(forGameName: gameName)
        for dir in coverSearchDirectories(gamePath: gamePath) {
            if let url = findCover(in: dir, matching: candidates) {
                return url
            }
        }
        return nil
    }

    @discardableResult
    func importCoverURLs(_ urls: [URL], forGameNamed gameName: String? = nil) -> String {
        var imported: [String] = []
        var rejected: [String] = []
        var failed: [String] = []

        if let gameName, urls.count == 1 {
            removeManagedCovers(forGameNamed: gameName)
        }

        for (index, sourceURL) in urls.enumerated() {
            let accessing = sourceURL.startAccessingSecurityScopedResource()
            defer { if accessing { sourceURL.stopAccessingSecurityScopedResource() } }

            let ext = normalizedImageExtension(for: sourceURL)
            guard Self.imageExtensions.contains(ext) else {
                rejected.append(sourceURL.lastPathComponent)
                continue
            }

            let baseName: String
            if let gameName, urls.count == 1 {
                baseName = preferredManagedCoverBaseName(forGameName: gameName)
            } else {
                let sourceBase = sourceURL.deletingPathExtension().lastPathComponent
                let fallback = sourceBase.isEmpty ? "cover_\(index + 1)" : sourceBase
                baseName = sanitizedCoverComponent(fallback)
            }

            let destination = primaryCoverDirectory.appendingPathComponent(baseName).appendingPathExtension(ext)
            do {
                if fileManager.fileExists(atPath: destination.path) {
                    try fileManager.removeItem(at: destination)
                }
                try fileManager.copyItem(at: sourceURL, to: destination)
                imported.append(sourceURL.lastPathComponent)
                NSLog("[ARMSX2 iOS Covers] imported %@ -> %@", sourceURL.lastPathComponent, destination.path)
            } catch {
                failed.append("\(sourceURL.lastPathComponent): \(error.localizedDescription)")
                NSLog("[ARMSX2 iOS Covers] import failed %@ -> %@ error=%@", sourceURL.lastPathComponent, destination.path, error.localizedDescription)
            }
        }

        var lines: [String] = []
        if !imported.isEmpty {
            let prefix = gameName == nil ? "Imported cover" : "Assigned cover"
            lines.append(imported.count == 1 ? "\(prefix): \(imported[0])" : "\(prefix)s: \(imported.count)")
        }
        if !rejected.isEmpty {
            lines.append("Unsupported image: \(rejected.joined(separator: ", "))")
        }
        if !failed.isEmpty {
            lines.append(failed.joined(separator: "\n"))
        }

        let message = lines.isEmpty ? "No covers imported." : lines.joined(separator: "\n")
        lastCoverMessage = message
        showCoverAlert = true
        return message
    }

    @discardableResult
    func removeManagedCovers(forGameNamed gameName: String) -> Int {
        let candidates = Set(coverBaseCandidates(forGameName: gameName).map { $0.lowercased() })
        var removed = 0

        for dir in managedCoverDirectories() {
            guard let files = try? fileManager.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.isRegularFileKey], options: [.skipsHiddenFiles]) else {
                continue
            }
            for file in files {
                let ext = file.pathExtension.lowercased()
                guard Self.imageExtensions.contains(ext) else { continue }
                let base = file.deletingPathExtension().lastPathComponent.lowercased()
                guard candidates.contains(base) else { continue }
                do {
                    try fileManager.removeItem(at: file)
                    removed += 1
                    NSLog("[ARMSX2 iOS Covers] removed %@", file.path)
                } catch {
                    NSLog("[ARMSX2 iOS Covers] remove failed %@ error=%@", file.path, error.localizedDescription)
                }
            }
        }

        if removed > 0 {
            lastCoverMessage = "Removed cover for \(displayName(forGameName: gameName))."
        } else {
            lastCoverMessage = "No managed cover found for \(displayName(forGameName: gameName))."
        }
        showCoverAlert = true
        return removed
    }

    func displayName(forGameName gameName: String) -> String {
        URL(fileURLWithPath: gameName).deletingPathExtension().lastPathComponent
    }

    private func managedCoverDirectories() -> [URL] {
        let docs = URL(fileURLWithPath: ARMSX2Bridge.documentsDirectory(), isDirectory: true)
        let primary = primaryCoverDirectory
        let legacy = docs.appendingPathComponent("covers", isDirectory: true)
        try? fileManager.createDirectory(at: legacy, withIntermediateDirectories: true)
        return uniqueURLs([primary, legacy])
    }

    private func coverSearchDirectories(gamePath: URL?) -> [URL] {
        let docs = URL(fileURLWithPath: ARMSX2Bridge.documentsDirectory(), isDirectory: true)
        let iso = URL(fileURLWithPath: ARMSX2Bridge.isoDirectory(), isDirectory: true)
        var dirs = managedCoverDirectories()
        if let gamePath {
            dirs.append(gamePath.deletingLastPathComponent())
        }
        dirs.append(iso)
        dirs.append(docs)
        return uniqueURLs(dirs)
    }

    private func findCover(in directory: URL, matching candidates: [String]) -> URL? {
        guard let files = try? fileManager.contentsOfDirectory(at: directory, includingPropertiesForKeys: [.isRegularFileKey], options: [.skipsHiddenFiles]) else {
            return nil
        }

        var lookup: [String: URL] = [:]
        for file in files {
            let ext = file.pathExtension.lowercased()
            guard Self.imageExtensions.contains(ext) else { continue }
            if let values = try? file.resourceValues(forKeys: [.isRegularFileKey]), values.isRegularFile == false {
                continue
            }
            lookup[file.lastPathComponent.lowercased()] = file
        }

        for candidate in candidates {
            let lower = candidate.lowercased()
            for ext in Self.imageExtensions {
                if let file = lookup["\(lower).\(ext)"] {
                    return file
                }
            }
        }

        return nil
    }

    private func coverBaseCandidates(forGameName gameName: String) -> [String] {
        let fileName = URL(fileURLWithPath: gameName).lastPathComponent
        let stem = URL(fileURLWithPath: fileName).deletingPathExtension().lastPathComponent
        let nestedStem = nestedDiscStem(from: stem)
        var raw: [String] = [stem, fileName]

        if let nestedStem, nestedStem != stem {
            raw.append(nestedStem)
        }
        raw.append(contentsOf: titleVariants(from: stem))
        if let nestedStem {
            raw.append(contentsOf: titleVariants(from: nestedStem))
        }
        raw.append(contentsOf: serialCandidates(from: stem))
        if let nestedStem {
            raw.append(contentsOf: serialCandidates(from: nestedStem))
        }

        var result: [String] = []
        var seen = Set<String>()
        for item in raw {
            for candidate in [item, sanitizedCoverComponent(item)] {
                let trimmed = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { continue }
                let key = trimmed.lowercased()
                if seen.insert(key).inserted {
                    result.append(trimmed)
                }
            }
        }
        return result
    }

    private func preferredManagedCoverBaseName(forGameName gameName: String) -> String {
        let stem = URL(fileURLWithPath: gameName).deletingPathExtension().lastPathComponent
        let sanitized = sanitizedCoverComponent(stem)
        return sanitized.isEmpty ? "cover" : sanitized
    }

    private func nestedDiscStem(from stem: String) -> String? {
        let url = URL(fileURLWithPath: stem)
        let nestedExt = url.pathExtension.lowercased()
        let discExtensions: Set<String> = ["iso", "img", "bin", "cso", "zso", "gz"]
        guard discExtensions.contains(nestedExt) else { return nil }
        let nestedStem = url.deletingPathExtension().lastPathComponent
        return nestedStem.isEmpty ? nil : nestedStem
    }

    private func titleVariants(from base: String) -> [String] {
        var variants: [String] = []
        variants.append(base.replacingOccurrences(of: "_", with: " "))
        variants.append(strippingBracketedTags(from: base.replacingOccurrences(of: "_", with: " ")))
        variants.append(base.replacingOccurrences(of: " - ", with: ": "))
        return variants
    }

    private func strippingBracketedTags(from base: String) -> String {
        var value = base
        for pattern in ["\\[[^\\]]*\\]", "\\([^\\)]*\\)", "\\{[^\\}]*\\}"] {
            value = value.replacingOccurrences(of: pattern, with: " ", options: .regularExpression)
        }
        return value.replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func serialCandidates(from base: String) -> [String] {
        let pattern = "^([A-Za-z]{4})[_-]?([0-9]{3})[._-]?([0-9]{2})"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let range = NSRange(base.startIndex..<base.endIndex, in: base)
        guard let match = regex.firstMatch(in: base, range: range),
              match.numberOfRanges >= 4,
              let prefixRange = Range(match.range(at: 1), in: base),
              let firstRange = Range(match.range(at: 2), in: base),
              let secondRange = Range(match.range(at: 3), in: base) else {
            return []
        }
        let prefix = String(base[prefixRange]).uppercased()
        let first = String(base[firstRange])
        let second = String(base[secondRange])
        return ["\(prefix)-\(first)\(second)", "\(prefix)_\(first).\(second)"]
    }

    private func sanitizedCoverComponent(_ input: String) -> String {
        var value = input.trimmingCharacters(in: .whitespacesAndNewlines)
        value = value.replacingOccurrences(of: "[\\\\/:*?\"<>|]", with: " ", options: .regularExpression)
        value = value.replacingOccurrences(of: "[^A-Za-z0-9._-]", with: "_", options: .regularExpression)
        value = value.replacingOccurrences(of: "_+", with: "_", options: .regularExpression)
        value = value.replacingOccurrences(of: "^_+|_+$", with: "", options: .regularExpression)
        return value
    }

    private func normalizedImageExtension(for url: URL) -> String {
        let ext = url.pathExtension.lowercased()
        return ext == "jpeg" ? "jpg" : ext
    }

    private func uniqueURLs(_ urls: [URL]) -> [URL] {
        var seen = Set<String>()
        var result: [URL] = []
        for url in urls {
            let key = url.standardizedFileURL.path
            if seen.insert(key).inserted {
                result.append(url)
            }
        }
        return result
    }
}
