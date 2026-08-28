import SwiftUI

@main
struct QISApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Muss vor Abschluss des App-Starts registriert werden, damit iOS den Handler kennt.
        BackgroundGradeRefresher.register()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
        }
        .onChange(of: scenePhase) { _, phase in
            // Beim Wechsel in den Hintergrund den nächsten Abruf einplanen (falls aktiviert).
            if phase == .background {
                BackgroundGradeRefresher.schedule()
            }
        }
    }
}
