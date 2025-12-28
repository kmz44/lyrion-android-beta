//
//  ContentView.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/4/24.
//

import SwiftData
import SwiftUI

struct ContentView: View {
    @EnvironmentObject var appManager: AppManager
    @EnvironmentObject var authManager: AuthManager
    @Environment(\.modelContext) var modelContext
    @Environment(LLMEvaluator.self) var llm
    @State var showOnboarding = false
    @State var showSettings = false
    @State var showChats = false
    @State var currentThread: Thread?
    @FocusState var isPromptFocused: Bool

    @Query var userProfiles: [UserProfile]
    @State var showUserOnboarding = false

    var body: some View {
        Group {
            if appManager.userInterfaceIdiom == .pad || appManager.userInterfaceIdiom == .vision {
                // iPad y Vision - NavigationSplitView
                NavigationSplitView {
                    ChatsListView(currentThread: $currentThread, isPromptFocused: $isPromptFocused)
                } detail: {
                    ChatView(currentThread: $currentThread, isPromptFocused: $isPromptFocused, showChats: $showChats, showSettings: $showSettings)
                }
            } else {
                // iPhone y Mac - Nueva navegación con barra de tabs
                MainTabView(currentThread: $currentThread, isPromptFocused: $isPromptFocused, showSettings: $showSettings, showChats: $showChats)
            }
        }
        .environmentObject(appManager)
        .environment(llm)
        .task {
            // Migrar perfiles antiguos: agregar campos de personalización si están vacíos
            if let profile = userProfiles.first {
                if profile.titleColorHex.isEmpty {
                    profile.titleColorHex = "#444444"
                    profile.subtitleColorHex = "#444444"
                    profile.buttonTextColorHex = "#444444"
                    profile.buttonBackgroundColorHex = "#FFFFFF"
                    profile.fontDesign = "rounded"
                    try? modelContext.save()
                }
                
                // Sincronizar Avatar de Google
                if let user = authManager.currentUser,
                   let avatarUrl = user.userMetadata?.avatarUrl {
                    // Solo actualizamos si es diferente para evitar escrituras innecesarias
                    if profile.avatarUrl != avatarUrl {
                        profile.avatarUrl = avatarUrl
                        try? modelContext.save()
                    }
                }
            }
            
            // Manejo inicial de sesión y perfiles
            if let user = authManager.currentUser {
                // Si ya estamos logueados al abrir la app (Auto-Login)
                print("🔄 Auto-Login detectado: Intentando sincronizar perfil...")
                
                // Intentar buscar perfil remoto
                if let remoteProfile = try? await SupabaseClient.shared.fetchCurrentUserProfile() {
                    print("✅ Perfil recuperado en inicio: \(remoteProfile.username ?? "Sin usuario")")
                    await MainActor.run {
                        // Verificar si el perfil está completo (tiene nombre)
                        // Si no tiene nombre, consideramos que es "incompleto" y forzamos onboarding
                        let hasName = !(remoteProfile.nombre?.isEmpty ?? true)
                        self.showUserOnboarding = !hasName
                        
                        if !hasName {
                            print("⚠️ Perfil incompleto (sin nombre). Forzando Onboarding.")
                        } else {
                            print("✅ Perfil completo. Ocultando onboarding.")
                        }
                        
                         // Sincronizar/Crear localmente
                        let currentUserId = user.id
                        var targetProfile = userProfiles.first { $0.userId == currentUserId }
                        
                        if targetProfile == nil {
                            let newProfile = UserProfile(
                                name: remoteProfile.nombre ?? "",
                                lastName: remoteProfile.apellido ?? "",
                                age: remoteProfile.edad ?? 18,
                                height: Double(remoteProfile.altura ?? 170),
                                weight: Double(remoteProfile.peso ?? 70),
                                maritalStatus: remoteProfile.estadoCivil ?? "",
                                country: remoteProfile.country ?? "",
                                state: remoteProfile.estadoRegion ?? "",
                                userId: currentUserId
                            )
                            modelContext.insert(newProfile)
                            targetProfile = newProfile
                        } else {
                            // Actualizar existente
                            targetProfile?.name = remoteProfile.nombre ?? ""
                            targetProfile?.lastName = remoteProfile.apellido ?? ""
                            targetProfile?.avatarUrl = remoteProfile.avatarUrl
                            // ... resto de campos si es necesario actualizar ...
                            targetProfile?.age = remoteProfile.edad ?? 18
                            targetProfile?.height = Double(remoteProfile.altura ?? 170)
                            targetProfile?.weight = Double(remoteProfile.peso ?? 70)
                            targetProfile?.maritalStatus = remoteProfile.estadoCivil ?? ""
                            targetProfile?.country = remoteProfile.country ?? ""
                            targetProfile?.state = remoteProfile.estadoRegion ?? ""
                        }
                        try? modelContext.save()
                    }
                } else {
                    print("⚠️ Usuario logueado pero SIN perfil remoto. Mostrando onboarding.")
                    await MainActor.run {
                        if userProfiles.isEmpty {
                            showUserOnboarding = true
                        }
                    }
                }
            } else {
                // No logueado
                if userProfiles.isEmpty {
                    showUserOnboarding = true
                }
            }
            // REMOVED: else if appManager.installedModels.count == 0 - Ya no forzamos el onboarding
            
            // Solo activar el teclado automáticamente en iPad/Mac, NO en iPhone
            if appManager.userInterfaceIdiom != .phone {
                isPromptFocused = true
            }
            
            if let modelName = appManager.currentModelName {
                _ = try? await llm.load(modelName: modelName)
            }
        }
        .onChange(of: authManager.currentUser) { oldValue, newUser in
            Task {
                if let currentUser = newUser {
                    // Verificar si el perfil local pertenece al usuario actual
                    let currentUserId = currentUser.id
                    
                    await MainActor.run {
                        // Limpieza Agresiva: Borrar cualquier perfil que no pertenezca al usuario actual
                        // Esto asegura que las vistas hijas (que usan @Query userProfiles) solo vean el perfil correcto.
                        let staleProfiles = userProfiles.filter { $0.userId != currentUserId }
                        
                        for profile in staleProfiles {
                            print("Borrando perfil obsoleto de: \(profile.userId ?? "nil")")
                            modelContext.delete(profile)
                        }
                        
                        if !staleProfiles.isEmpty {
                            try? modelContext.save()
                        }
                    }
                    
                    // Sincronizar desde Supabase
                    if let remoteProfile = try? await SupabaseClient.shared.fetchCurrentUserProfile() {
                        print("Perfil remoto encontrado: \(remoteProfile)")
                        
                        await MainActor.run {
                            // Usuario existente:
                            // Verificar si está completo (tiene nombre)
                            let hasName = !(remoteProfile.nombre?.isEmpty ?? true)
                            // Si NO tiene nombre, mostramos onboarding (true). Si tiene, ocultamos (false).
                            self.showUserOnboarding = !hasName
                            
                            if !hasName {
                                print("⚠️ Perfil incompleto (sin nombre) tras login. Forzando Onboarding.")
                            }
                            
                            // Verificar de nuevo si se creó uno o si usamos el existente
                            var targetProfile = userProfiles.first { $0.userId == currentUserId }
                            
                            if targetProfile == nil {
                                // Crear nuevo perfil si no existe
                                let newProfile = UserProfile(
                                    name: remoteProfile.nombre ?? "",
                                    lastName: remoteProfile.apellido ?? "",
                                    age: remoteProfile.edad ?? 18,
                                    height: Double(remoteProfile.altura ?? 170),
                                    weight: Double(remoteProfile.peso ?? 70),
                                    maritalStatus: remoteProfile.estadoCivil ?? "",
                                    country: remoteProfile.country ?? "",
                                    state: remoteProfile.estadoRegion ?? "",
                                    userId: currentUserId
                                )
                                modelContext.insert(newProfile)
                                targetProfile = newProfile
                            }
                            
                            if let profile = targetProfile {
                                if let nombre = remoteProfile.nombre { profile.name = nombre }
                                if let apellido = remoteProfile.apellido { profile.lastName = apellido }
                                if let url = remoteProfile.avatarUrl { profile.avatarUrl = url }
                                if let edad = remoteProfile.edad { profile.age = edad }
                                if let altura = remoteProfile.altura { profile.height = Double(altura) }
                                if let peso = remoteProfile.peso { profile.weight = Double(peso) }
                                if let estado = remoteProfile.estadoCivil { profile.maritalStatus = estado }
                                if let pais = remoteProfile.country { profile.country = pais }
                                if let region = remoteProfile.estadoRegion { profile.state = region }
                                
                                try? modelContext.save()
                            }
                        }
                    } else {
                        // Si no hay perfil remoto, significa que es un usuario NUEVO (o sin datos)
                         await MainActor.run {
                             // Solo mostrar onboarding si realmente no tenemos datos locales válidos tampoco
                             // O mejor aún, SIEMPRE mostrar onboarding si no hay datos en la nube para asegurar que rellene su info
                             print("🆕 Usuario nuevo detectado. Mostrando Onboarding.")
                             showUserOnboarding = true
                             
                             // Crear placeholder local para que el onboarding tenga donde guardar
                             if !userProfiles.contains(where: { $0.userId == currentUserId }) {
                                 let newProfile = UserProfile(
                                     name: "",
                                     lastName: "",
                                     age: 0,
                                     height: 0,
                                     weight: 0,
                                     maritalStatus: "",
                                     country: "",
                                     state: "",
                                     userId: currentUserId
                                 )
                                 modelContext.insert(newProfile)
                                 try? modelContext.save()
                             }
                         }
                    }
                } else {
                    // Cierro Sesión (currentUser es nil) - Limpieza TOTAL
                    await MainActor.run {
                        print("🔒 Cerrando sesión: Borrando todos los datos locales.")
                        
                        // Borrar perfiles de usuario
                        for profile in userProfiles {
                            modelContext.delete(profile)
                        }
                        
                        // Borrar todos los mensajes locales (LocalMessage)
                        do {
                            let descriptor = FetchDescriptor<LocalMessage>()
                            let localMessages = try modelContext.fetch(descriptor)
                            for message in localMessages {
                                modelContext.delete(message)
                            }
                            print("🗑️ Borrados \(localMessages.count) mensajes locales")
                        } catch {
                            print("❌ Error borrando mensajes locales: \(error)")
                        }
                        
                        try? modelContext.save()
                    }
                }
            }
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(currentThread: $currentThread)
                .environmentObject(appManager)
                .environment(llm)
                .presentationDragIndicator(.hidden)
                .if(appManager.userInterfaceIdiom == .phone) { view in
                    view.presentationDetents([.medium])
                }
        }
        .sheet(isPresented: $showOnboarding, onDismiss: dismissOnboarding) {
            OnboardingView(showOnboarding: $showOnboarding)
                .environment(llm)
            // REMOVED: .interactiveDismissDisabled - Usuario puede cerrar siempre
        }
        .fullScreenCover(isPresented: $showUserOnboarding, onDismiss: {
            // REMOVED: if appManager.installedModels.count == 0 - Ya no forzamos
        }) {
            UserOnboardingView(showOnboarding: $showUserOnboarding)
        }
        #if !os(visionOS)
        .tint(LyrionTheme.primaryPurple)
        #endif
        .fontDesign(appManager.appFontDesign.getFontDesign())
        .environment(\.dynamicTypeSize, appManager.appFontSize.getFontSize())
        .fontWidth(appManager.appFontWidth.getFontWidth())
        .preferredColorScheme(appManager.appColorScheme.colorScheme)
        .onAppear {
            appManager.incrementNumberOfVisits()
        }
    }
    
    func dismissOnboarding() {
        // Solo activar el teclado automáticamente en iPad/Mac, NO en iPhone
        if appManager.userInterfaceIdiom != .phone {
            isPromptFocused = true
        }
    }
}

extension View {
    @ViewBuilder func `if`<Content: View>(_ condition: Bool, transform: (Self) -> Content) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }
}

#Preview {
    ContentView()
}
