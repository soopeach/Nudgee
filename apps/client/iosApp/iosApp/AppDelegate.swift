import SwiftUI
import ComposeApp

@main
struct NudgeeApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeViewController()
                .ignoresSafeArea(.all)
                .onOpenURL { url in
                    IosDeeplinkHandlerKt.handleNudgeeDeeplink(url: url)
                }
        }
    }
}

private struct ComposeViewController: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
