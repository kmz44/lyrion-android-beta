//
//  AuthManager.swift
//  Lyrion
//

import SwiftUI

@MainActor
class AuthManager: ObservableObject {
    @Published var isAuthenticated = false
    @Published var currentUser: User?
    @Published var isLoading = false
    @Published var errorMessage: String?
    // ELIMINADO: setupCompleted ya no es necesario
    
    private let supabase = SupabaseClient.shared
    
    init() {
        // Cargar sesión guardada si existe
        if let session = supabase.loadSession() {
            self.isAuthenticated = true
            self.currentUser = session.user
        }
        // ELIMINADO: Verificación de setupCompleted
    }
    
    func signInWithGoogle() async {
        isLoading = true
        errorMessage = nil
        
        do {
            let authURL = try await supabase.signInWithGoogle()
            
            // Abrir URL en Safari
            #if os(iOS)
            if UIApplication.shared.canOpenURL(authURL) {
                await UIApplication.shared.open(authURL)
            }
            #elseif os(macOS)
            NSWorkspace.shared.open(authURL)
            #endif
        } catch {
            errorMessage = error.localizedDescription
        }
        
        isLoading = false
    }
    
    func handleAuthCallback(url: URL) async {
        isLoading = true
        errorMessage = nil
        
        do {
            let session = try await supabase.handleAuthCallback(url: url)
            
            // Guardar sesión
            supabase.saveSession(session)
            
            // Actualizar estado
            self.isAuthenticated = true
            self.currentUser = session.user
        } catch {
            errorMessage = error.localizedDescription
        }
        
        isLoading = false
    }
    
    func signOut() {
        supabase.signOut()
        self.isAuthenticated = false
        self.currentUser = nil
        
        // Limpiar datos de usuario (UserDefaults ya no se usa para esto)
        // Los datos ahora están en SwiftData
    }
    
    func checkSessionValidity() async {
        guard isAuthenticated else { return }
        
        print("🔍 Checking session validity on app resume...")
        do {
            try await supabase.refreshSession()
            // Si el refresh funciona, actualizamos el usuario por si acaso
            if let session = supabase.loadSession() {
                self.currentUser = session.user
            }
        } catch {
            print("❌ Session invalid or expired: \(error.localizedDescription)")
            
            // Ignore network errors, only sign out on auth errors (e.g. 400/401 implied by specific error cases or if not network)
            let nsError = error as NSError
            let networkErrors = [NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost, NSURLErrorTimedOut]
            if networkErrors.contains(nsError.code) {
                 print("⚠️ Network error detected. Keeping local session active.")
                 return
            }
            
            signOut()
        }
    }
}
