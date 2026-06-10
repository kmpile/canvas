import SwiftUI
import Canvas
import UIKit

/// iOS host for the multiplatform canvas (the `:canvas` module's `CanvasViewController`, exported in
/// the `Canvas` framework). A thin Compose-hosting app.
///
/// Wraps the Compose VC in a `CanvasShortcutsHostController` that surfaces every canvas action in
/// iOS's **Discoverability HUD** (hold ⌘ to reveal) via `UIKeyCommand`, dispatching presses back
/// into Compose state through `CanvasHost.controller`. The shared `CanvasShortcuts` table is the
/// single source of truth — adding an entry there lights up the HUD entry on next build.
@main
struct CanvasApp: App {
    var body: some Scene {
        WindowGroup {
            CanvasView()
                .ignoresSafeArea(.all)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }
}

private struct CanvasView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let host = CanvasViewControllerKt.createCanvasHost()
        return CanvasShortcutsHostController(host: host)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Parent VC that embeds the Compose VC as a child and surfaces `CanvasHost.shortcuts` in the iOS
/// **Discoverability HUD**. Each `UIKeyCommand` carries the shortcut's stable `id`; the action
/// selector dispatches it through the shared `CanvasController` so the in-pill state updates exactly
/// as if the user had tapped the pill switch.
///
/// `pan` (Space-hold) is intentionally **not** registered — `UIKeyCommand` would intercept Space
/// and break the modeless down/up tracking; Compose's own `onKeyEvent` (via `UIPress`) handles it.
private final class CanvasShortcutsHostController: UIViewController {
    private let host: CanvasHost

    init(host: CanvasHost) {
        self.host = host
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    override func viewDidLoad() {
        super.viewDidLoad()
        let child = host.viewController
        addChild(child)
        view.addSubview(child.view)
        child.view.frame = view.bounds
        child.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        child.didMove(toParent: self)
    }

    override var canBecomeFirstResponder: Bool { true }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        becomeFirstResponder()
    }

    override var keyCommands: [UIKeyCommand]? {
        host.shortcuts.compactMap { shortcut -> UIKeyCommand? in
            guard let input = shortcut.key, shortcut.id != "pan" else { return nil }
            var flags: UIKeyModifierFlags = []
            if shortcut.cmd { flags.insert(.command) }
            if shortcut.shift { flags.insert(.shift) }
            if shortcut.alt { flags.insert(.alternate) }
            let command = UIKeyCommand(
                input: input,
                modifierFlags: flags,
                action: #selector(handleShortcut(_:))
            )
            command.discoverabilityTitle = shortcut.label
            return command
        }
    }

    /// Identify which shortcut fired by matching the sender's input + modifier flags back against
    /// the shared table. The combinations are unique by design.
    @objc private func handleShortcut(_ sender: UIKeyCommand) {
        let input = sender.input
        let flags = sender.modifierFlags
        let match = host.shortcuts.first { s in
            s.key == input &&
                s.cmd == flags.contains(.command) &&
                s.shift == flags.contains(.shift) &&
                s.alt == flags.contains(.alternate)
        }
        if let id = match?.id {
            host.controller.dispatch(id: id)
        }
    }
}
