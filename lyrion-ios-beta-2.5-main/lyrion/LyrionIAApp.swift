//
//  LyrionApp.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/4/24.
//

import SwiftUI
import MLXLLM
import SwiftData

@main
struct LyrionApp: App {
    #if os(macOS)
    @NSApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    #endif
    @StateObject var appManager = AppManager()
    @StateObject var authManager = AuthManager()
    @State var llm = LLMEvaluator()
    
    // Heartbeat timer for user presence
    @State private var heartbeatTimer: Timer?
    
    init() {
        // Configure URLCache for offline image support
        let memoryCapacity = 50 * 1024 * 1024 // 50 MB
        let diskCapacity = 500 * 1024 * 1024 // 500 MB
        let cache = URLCache(memoryCapacity: memoryCapacity, diskCapacity: diskCapacity, diskPath: "lyrion_image_cache")
        URLCache.shared = cache
    }
    
    @Environment(\.scenePhase) var scenePhase
    
    var body: some Scene {
        WindowGroup {
            Group {
                if authManager.isAuthenticated {
                    // ELIMINADO: Ya no revisamos setupCompleted aquí
                    // ContentView manejará el onboarding de usuario vía UserOnboardingView
                    ContentView()
                        .environmentObject(appManager)
                        .environmentObject(authManager)
                        .environment(llm)
                        .environment(DeviceStat())
                        .modifier(DataInitializer(appManager: appManager))
                } else {
                    LoginView()
                        .environmentObject(authManager)
                        .environmentObject(appManager)
                }
            }
            .onOpenURL { url in
                // Manejar callback de autenticación
                if url.scheme == "lyrion" && url.host == "auth-callback" {
                    Task {
                        await authManager.handleAuthCallback(url: url)
                    }
                }
            }
            .onChange(of: scenePhase) { newPhase in
                switch newPhase {
                case .active:
                    Task {
                        await authManager.checkSessionValidity()
                        // User Presence: Active
                        try? await SupabaseClient.shared.setUserActive(true)
                    }
                    // Start heartbeat timer (every 60 seconds)
                    heartbeatTimer?.invalidate()
                    heartbeatTimer = Timer.scheduledTimer(withTimeInterval: 60.0, repeats: true) { _ in
                        Task {
                            try? await SupabaseClient.shared.setUserActive(true)
                        }
                    }
                case .background, .inactive:
                    // Stop heartbeat timer
                    heartbeatTimer?.invalidate()
                    heartbeatTimer = nil
                    Task {
                        // User Presence: Inactive
                        try? await SupabaseClient.shared.setUserActive(false)
                    }
                @unknown default:
                    break
                }
            }
            .modelContainer(for: [Thread.self, Message.self, UserProfile.self, UserAttribute.self, LocalMessage.self])
            #if os(macOS) || os(visionOS)
            .frame(minWidth: 640, maxWidth: .infinity, minHeight: 420, maxHeight: .infinity)
            #if os(macOS)
            .onAppear {
                NSWindow.allowsAutomaticWindowTabbing = false
            }
            #endif
            #endif
        }
        #if os(visionOS)
        .windowResizability(.contentSize)
        #endif
        #if os(macOS)
        .commands {
            CommandGroup(replacing: .newItem) {
                Button("Show Main Window") {
                    if let mainWindow = NSApp.windows.first {
                        mainWindow.makeKeyAndOrderFront(nil)
                    }
                }
            }
        }
        #endif
    }
}

struct DataInitializer: ViewModifier {
    @ObservedObject var appManager: AppManager
    @Environment(\.modelContext) private var modelContext
    
    func body(content: Content) -> some View {
        content
            .onAppear {
                // Reiniciar atributos al inicio de la app
                appManager.resetAndInitializeAttributes(modelContext: modelContext)
            }
    }
}

#if os(macOS)
class AppDelegate: NSObject, NSApplicationDelegate, NSWindowDelegate {
    private var closedWindowsStack = [NSWindow]()
    
    func applicationDidFinishLaunching(_ notification: Notification) {
        let mainWindow = NSApp.windows.first
        mainWindow?.delegate = self
    }
    
    func applicationShouldHandleReopen(_ sender: NSApplication, hasVisibleWindows flag: Bool) -> Bool {
        // if there's a recently closed window, bring that back
        if let lastClosed = closedWindowsStack.popLast() {
            lastClosed.makeKeyAndOrderFront(self)
        } else {
            // otherwise, un-minimize any minimized windows
            for window in sender.windows where window.isMiniaturized {
                window.deminiaturize(nil)
            }
        }
        return false
    }
    
    func windowWillClose(_ notification: Notification) {
        if let window = notification.object as? NSWindow {
            closedWindowsStack.append(window)
        }
    }
}
#endif
