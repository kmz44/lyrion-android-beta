//
//  ModelsSettingsView.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/5/24.
//

import SwiftUI
import MLXLMCommon

struct ModelsSettingsView: View {
    @EnvironmentObject var appManager: AppManager
    @Environment(LLMEvaluator.self) var llm
    @State var showOnboardingInstallModelView = false
    @State var showImportCustomModelView = false
    
    var body: some View {
        Form {
            // Agrupar modelos instalados por categoría
            if !appManager.installedModels.isEmpty {
                let installedModelConfigs = appManager.installedModels.compactMap { modelName -> ModelConfiguration? in
                    ModelConfiguration.getModelByName(modelName) ?? ModelConfiguration.getModelByID(modelName)
                }
                
                let groupedModels = ModelConfiguration.groupModelsByCategory(installedModelConfigs)
                
                // Mostrar cada categoría de modelos instalados
                ForEach(ModelConfiguration.categoryOrder, id: \.self) { category in
                    if let modelsInCategory = groupedModels[category], !modelsInCategory.isEmpty {
                        Section {
                            ForEach(modelsInCategory, id: \.name) { model in
                                Button {
                                    Task {
                                        await switchModel(model.name)
                                    }
                                } label: {
                                    HStack {
                                        Label {
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(appManager.modelDisplayName(model.name))
                                                    .tint(.primary)
                                                
                                                // Indicador de modelo activo
                                                if appManager.currentModelName == model.name {
                                                    Text("Activo ahora")
                                                        .font(.caption2)
                                                        .foregroundColor(.green)
                                                }
                                            }
                                        } icon: {
                                            Image(systemName: appManager.currentModelName == model.name ? "checkmark.circle.fill" : "circle")
                                                .foregroundColor(appManager.currentModelName == model.name ? .green : .gray)
                                        }
                                        
                                        Spacer()
                                        
                                        // Icono de categoría
                                        Image(systemName: model.categoryIcon)
                                            .font(.caption)
                                            .foregroundColor(.secondary)
                                    }
                                }
                                #if os(macOS)
                                .buttonStyle(.borderless)
                                #endif
                            }
                        } header: {
                            Label(modelsInCategory.first?.categoryDisplayName ?? "Instalados", systemImage: modelsInCategory.first?.categoryIcon ?? "circle")
                        }
                    }
                }
            }
            
            Section {
                Button {
                    showOnboardingInstallModelView.toggle()
                } label: {
                    Label(NSLocalizedString("models.install_model", comment: ""), systemImage: "arrow.down.circle.dotted")
                }
                #if os(macOS)
                .buttonStyle(.borderless)
                #endif
                
                Button {
                    showImportCustomModelView.toggle()
                } label: {
                    Label("Importar Modelo Personalizado", systemImage: "square.and.arrow.down.on.square")
                }
                #if os(macOS)
                .buttonStyle(.borderless)
                #endif
            }
            
            // Sección de modelos personalizados importados
            if !appManager.customModels.isEmpty {
                Section(header: Text("Modelos Personalizados")) {
                    ForEach(appManager.customModels, id: \.self) { customID in
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(appManager.modelDisplayName(customID))
                                    .font(.body)
                                Text(customID)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            
                            Spacer()
                            
                            Button(role: .destructive) {
                                appManager.removeCustomModel(customID)
                            } label: {
                                Image(systemName: "trash")
                                    .foregroundColor(.red)
                            }
                            #if os(macOS)
                            .buttonStyle(.borderless)
                            #endif
                        }
                    }
                }
            }
        }
        .formStyle(.grouped)
        .navigationTitle(NSLocalizedString("models.title", comment: ""))
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
        .sheet(isPresented: $showImportCustomModelView) {
            ImportCustomModelView()
                .environmentObject(appManager)
        }
        .sheet(isPresented: $showOnboardingInstallModelView) {
            NavigationStack {
                OnboardingInstallModelView(showOnboarding: $showOnboardingInstallModelView)
                    .environment(llm)
                    .toolbar {
                        #if os(iOS) || os(visionOS)
                        ToolbarItem(placement: .topBarLeading) {
                            Button(action: { showOnboardingInstallModelView = false }) {
                                Image(systemName: "xmark")
                            }
                        }
                        #elseif os(macOS)
                        ToolbarItem(placement: .destructiveAction) {
                            Button(action: { showOnboardingInstallModelView = false }) {
                                Text(NSLocalizedString("common.close", comment: ""))
                            }
                        }
                        #endif
                    }
            }
        }
    }
    
    private func switchModel(_ modelName: String) async {
        // Buscar en modelos predefinidos
        if let model = ModelConfiguration.availableModels.first(where: {
            $0.name == modelName
        }) {
            appManager.currentModelName = modelName
            appManager.playHaptic()
            await llm.switchModel(model)
            return
        }
        
        // Buscar en modelos personalizados
        for customID in appManager.customModels {
            let customModel = ModelConfiguration.getModelByID(customID)
            if customModel.name == modelName {
                appManager.currentModelName = modelName
                appManager.playHaptic()
                await llm.switchModel(customModel)
                return
            }
        }
    }
}

#Preview {
    ModelsSettingsView()
}
