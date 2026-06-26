import SwiftUI
import ClientApp

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.black)
                .ignoresSafeArea(.all)
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
