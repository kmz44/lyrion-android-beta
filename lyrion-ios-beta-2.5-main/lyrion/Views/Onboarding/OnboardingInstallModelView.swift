//
//  OnboardingInstallModelView.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/4/24.
//

import MLXLMCommon
import os
import SwiftUI

struct OnboardingInstallModelView: View {
    @EnvironmentObject var appManager: AppManager
    @State private var deviceSupportsMetal3: Bool = true
    @Binding var showOnboarding: Bool
    @State var selectedModel = ModelConfiguration.defaultModel
    let suggestedModel = ModelConfiguration.defaultModel

    func sizeBadge(_ model: ModelConfiguration?) -> String? {
        guard let size = model?.modelSize else { return nil }
        return "\(size) GB"
    }

    /// The maximum allowable model size as a fraction of the device's total RAM.
    /// AJUSTADO: Para 4GB RAM (iPhone 16e), permitir máximo 1.5GB (37.5%)
    /// Esto previene crashes por falta de recursos
    let modelMemoryThreshold = 0.375

    var modelsList: some View {
        Form {
            Section {
                VStack(spacing: 12) {
                    Image(systemName: "arrow.down.circle.dotted")
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(width: 64, height: 64)
                        .foregroundStyle(.primary, .tertiary)

                    VStack(spacing: 4) {
                        Text(NSLocalizedString("onboarding.install.title", comment: ""))
                            .font(.title)
                            .fontWeight(.semibold)
                        Text(NSLocalizedString("onboarding.install.description", comment: ""))
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                        #if DEBUG
                        Text("ram: \(appManager.availableMemory) GB")
                            .foregroundStyle(.red)
                        #endif
                    }
                }
                .padding(.vertical)
                .frame(maxWidth: .infinity)
            }
            .listRowBackground(Color.clear)

            if appManager.installedModels.count > 0 {
                Section(header: Text(NSLocalizedString("onboarding.install.installed", comment: ""))) {
                    ForEach(appManager.installedModels, id: \.self) { modelName in
                        // Buscar en modelos predefinidos primero
                        let model = ModelConfiguration.getModelByName(modelName) ?? 
                                   // Si no existe, buscar por ID en modelos personalizados
                                   getCustomModel(modelName)
                        
                        Button {} label: {
                            Label {
                                Text(appManager.modelDisplayName(modelName))
                            } icon: {
                                Image(systemName: "checkmark")
                            }
                        }
                        .badge(sizeBadge(model))
                        #if os(macOS)
                            .buttonStyle(.borderless)
                        #endif
                            .foregroundStyle(.secondary)
                            .disabled(true)
                    }
                }
            } else {
                Section(header: Text(NSLocalizedString("onboarding.install.suggested", comment: ""))) {
                    Button { selectedModel = suggestedModel } label: {
                        Label {
                            Text(appManager.modelDisplayName(suggestedModel.name))
                                .tint(.primary)
                        } icon: {
                            Image(systemName: selectedModel.name == suggestedModel.name ? "checkmark.circle.fill" : "circle")
                        }
                    }
                    .badge(sizeBadge(suggestedModel))
                    #if os(macOS)
                        .buttonStyle(.borderless)
                    #endif
                }
            }

            if filteredModels.count > 0 {
                // Agrupar modelos por categoría
                let groupedModels = ModelConfiguration.groupModelsByCategory(filteredModels)
                
                // Mostrar cada categoría
                ForEach(ModelConfiguration.categoryOrder, id: \.self) { category in
                    if let modelsInCategory = groupedModels[category], !modelsInCategory.isEmpty {
                        Section {
                            ForEach(modelsInCategory, id: \.name) { model in
                                Button { selectedModel = model } label: {
                                    HStack {
                                        Label {
                                            VStack(alignment: .leading, spacing: 4) {
                                                Text(appManager.modelDisplayName(model.name))
                                                    .tint(.primary)
                                                
                                                // Mostrar categoría como badge
                                                Text(model.categoryDisplayName)
                                                    .font(.caption2)
                                                    .foregroundColor(.secondary)
                                            }
                                        } icon: {
                                            Image(systemName: selectedModel.name == model.name ? "checkmark.circle.fill" : "circle")
                                        }
                                        
                                        Spacer()
                                        
                                        if let badge = sizeBadge(model) {
                                            Text(badge)
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                    }
                                }
                                #if os(macOS)
                                    .buttonStyle(.borderless)
                                #endif
                            }
                        } header: {
                            Label(modelsInCategory.first?.categoryDisplayName ?? "", systemImage: modelsInCategory.first?.categoryIcon ?? "")
                        } footer: {
                            Text(modelsInCategory.first?.categoryDescription ?? "")
                                .font(.caption)
                        }
                    }
                }
            }

            #if os(macOS)
            Section {} footer: {
                NavigationLink(destination: OnboardingDownloadingModelProgressView(showOnboarding: $showOnboarding, selectedModel: $selectedModel)) {
                    Text(NSLocalizedString("onboarding.install.button", comment: ""))
                        .buttonStyle(.borderedProminent)
                }
                .disabled(filteredModels.isEmpty)
            }
            .padding()
            #endif
        }
        .formStyle(.grouped)
    }

    var body: some View {
        ZStack {
            if deviceSupportsMetal3 {
                modelsList
                #if os(iOS) || os(visionOS)
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        NavigationLink(destination: OnboardingDownloadingModelProgressView(showOnboarding: $showOnboarding, selectedModel: $selectedModel)) {
                            Text(NSLocalizedString("onboarding.install.button", comment: ""))
                                .font(.headline)
                        }
                        .disabled(filteredModels.isEmpty)
                    }
                }
                .listStyle(.insetGrouped)
                #endif
                .task {
                    checkModels()
                }
            } else {
                DeviceNotSupportedView()
            }
        }
        .onAppear {
            checkMetal3Support()
        }
    }

    var filteredModels: [ModelConfiguration] {
        // Incluir modelos predefinidos + modelos personalizados
        let allModels = ModelConfiguration.getAllAvailableModels(customModels: appManager.customModels)
        
        return allModels
            .filter { !appManager.installedModels.contains($0.name) }
            .filter { model in
                !(appManager.installedModels.isEmpty && model.name == suggestedModel.name)
            }
            .filter { model in
                // Para modelos personalizados sin tamaño conocido, permitir instalación
                guard let size = model.modelSize else { return true }
                return size <= Decimal(modelMemoryThreshold * appManager.availableMemory)
            }
            .sorted { $0.name < $1.name }
    }

    func checkModels() {
        // automatically select the first available model
        if appManager.installedModels.contains(suggestedModel.name) {
            if let model = filteredModels.first {
                selectedModel = model
            }
        }
    }

    func checkMetal3Support() {
        #if os(iOS)
        if let device = MTLCreateSystemDefaultDevice() {
            deviceSupportsMetal3 = device.supportsFamily(.metal3)
        }
        #endif
    }
    
    /// Obtiene un modelo personalizado por nombre
    private func getCustomModel(_ modelName: String) -> ModelConfiguration? {
        for customID in appManager.customModels {
            let customModel = ModelConfiguration.getModelByID(customID)
            if customModel.name == modelName {
                return customModel
            }
        }
        return nil
    }
}

#Preview {
    @Previewable @State var appManager = AppManager()

    OnboardingInstallModelView(showOnboarding: .constant(true))
        .environmentObject(appManager)
}
