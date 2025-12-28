//
//  LanguageSettingsView.swift
//  Lyrion
//
//  Language selection view
//

import SwiftUI

struct LanguageSettingsView: View {
    @AppStorage("appLanguage") private var appLanguage: String = Locale.current.language.languageCode?.identifier ?? "en"
    @Environment(\.dismiss) var dismiss
    @State private var showRestartAlert = false
    
    var body: some View {
        Form {
            Section {
                Button(action: {
                    if appLanguage != "es" {
                        appLanguage = "es"
                        UserDefaults.standard.set(["es"], forKey: "AppleLanguages")
                        UserDefaults.standard.synchronize()
                        showRestartAlert = true
                    }
                }) {
                    HStack {
                        Text("🇪🇸")
                            .font(.title2)
                        Text(NSLocalizedString("settings.language.spanish", comment: ""))
                            .foregroundStyle(.primary)
                        Spacer()
                        if appLanguage == "es" {
                            Image(systemName: "checkmark")
                                .foregroundStyle(LyrionTheme.primaryPurple)
                        }
                    }
                }
                
                Button(action: {
                    if appLanguage != "en" {
                        appLanguage = "en"
                        UserDefaults.standard.set(["en"], forKey: "AppleLanguages")
                        UserDefaults.standard.synchronize()
                        showRestartAlert = true
                    }
                }) {
                    HStack {
                        Text("🇺🇸")
                            .font(.title2)
                        Text(NSLocalizedString("settings.language.english", comment: ""))
                            .foregroundStyle(.primary)
                        Spacer()
                        if appLanguage == "en" {
                            Image(systemName: "checkmark")
                                .foregroundStyle(LyrionTheme.primaryPurple)
                        }
                    }
                }
            } header: {
                Text(NSLocalizedString("settings.language.title", comment: ""))
            }
        }
        .navigationTitle(NSLocalizedString("settings.language", comment: ""))
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
        .alert(appLanguage == "es" ? "Idioma cambiado" : "Language changed", isPresented: $showRestartAlert) {
            Button(appLanguage == "es" ? "Reiniciar" : "Restart") {
                exit(0)
            }
            Button(appLanguage == "es" ? "Más tarde" : "Later", role: .cancel) {
                dismiss()
            }
        } message: {
            Text(appLanguage == "es" 
                ? "La aplicación necesita reiniciarse para aplicar el nuevo idioma."
                : "The app needs to restart to apply the new language.")
        }
    }
}

#Preview {
    NavigationStack {
        LanguageSettingsView()
    }
}
