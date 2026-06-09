// MetalGameView.swift — UIViewRepresentable wrapper for CAMetalLayer game view
// SPDX-License-Identifier: GPL-3.0+

import SwiftUI
import UIKit

struct MetalGameView: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        let view = ARMSX2Bridge.gameRenderView()
        applyIOS27TouchPolicy(to: view)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        // drawableSize update is handled by layoutSubviews
        applyIOS27TouchPolicy(to: uiView)
    }

    private func applyIOS27TouchPolicy(to uiView: UIView) {
        if #available(iOS 27.0, *) {
            guard uiView.isUserInteractionEnabled else { return }
            uiView.isUserInteractionEnabled = false
            NSLog("@@IOS27_TOUCH_POLICY@@ metal_render_view_interactive=0")
        }
    }
}
