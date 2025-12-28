//
//  HomeAISettingsView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/3/24.
//

import SwiftUI

struct HomeAISettingsView: View {
    @EnvironmentObject var appManager: AppManager
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Modelo de Voz para Inicio"), footer: Text("Selecciona qué modelo de IA responderá cuando hables en la pantalla de inicio. Si seleccionas 'Usar modelo global', se usará el mismo que en el chat.")) {
                    
                    Picker("Modelo", selection: $appManager.homeModelName) {
                        Text("Usar modelo global").tag("")
                        
                        ForEach(appManager.installedModels, id: \.self) { model in
                            Text(appManager.modelDisplayName(model)).tag(model)
                        }
                    }
                }
                
                Section(header: Text("Información")) {
                    if appManager.homeModelName.isEmpty {
                        Text("Actualmente usando: \(appManager.modelDisplayName(appManager.currentModelName ?? "Ninguno"))")
                            .foregroundColor(.secondary)
                    } else {
                        Text("Modelo específico seleccionado.")
                            .foregroundColor(.green)
                    }
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Soporte de Razonamiento", systemImage: "brain.head.profile")
                            .font(.headline)
                        Text("Si seleccionas un modelo de razonamiento (ej. DeepSeek R1), la app esperará a que termine de 'pensar' antes de hablarte.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                }
            }
            .navigationTitle("IA de Inicio")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Listo") {
                        dismiss()
                    }
                }
            }
        }
    }
}
