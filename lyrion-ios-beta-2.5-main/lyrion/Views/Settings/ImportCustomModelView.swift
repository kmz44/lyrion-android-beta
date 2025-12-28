//
//  ImportCustomModelView.swift
//  Lyrion
//
//  Sistema de importación de modelos personalizados desde Hugging Face o disco local
//

import Foundation
import SwiftUI
import MLXLMCommon
import UniformTypeIdentifiers

#if os(iOS)
import UIKit
#endif

struct ImportCustomModelView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appManager: AppManager
    @State private var modelID: String = ""
    @State private var isValidating: Bool = false
    @State private var errorMessage: String?
    @State private var showSuccess: Bool = false
    @State private var importMethod: ImportMethod = .huggingFace
    @State private var showDocumentPicker: Bool = false
    @State private var selectedFolderURL: URL?
    
    enum ImportMethod {
        case huggingFace
        case localStorage
    }
    
    var body: some View {
        NavigationStack {
            Form {
                // Selector de método de importación
                Section {
                    Picker("Método de Importación", selection: $importMethod) {
                        Label("Desde Hugging Face", systemImage: "cloud.fill")
                            .tag(ImportMethod.huggingFace)
                        Label("Desde Mi Dispositivo", systemImage: "folder.fill")
                            .tag(ImportMethod.localStorage)
                    }
                    .pickerStyle(.segmented)
                }
                
                // Contenido según el método seleccionado
                if importMethod == .huggingFace {
                    huggingFaceSection
                } else {
                    localStorageSection
                }
                if let errorMessage = errorMessage {
                    Section {
                        Label(errorMessage, systemImage: "exclamationmark.triangle")
                            .foregroundColor(.red)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle("Importar Modelo")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button("Importar") {
                        if importMethod == .huggingFace {
                            importModelFromHuggingFace()
                        } else {
                            showDocumentPicker = true
                        }
                    }
                    .disabled(importMethod == .huggingFace && (modelID.isEmpty || isValidating))
                }
            }
            #if os(iOS)
            .sheet(isPresented: $showDocumentPicker) {
                DocumentPicker(selectedURL: $selectedFolderURL, onImport: importModelFromLocalStorage)
            }
            #endif
            .alert("¡Modelo Importado!", isPresented: $showSuccess) {
                Button("Usar Ahora") {
                    dismiss()
                }
                Button("OK") {
                    dismiss()
                }
            } message: {
                if importMethod == .huggingFace {
                    Text("El modelo \(modelID) ha sido agregado. Ve a 'Instalar modelo' para descargarlo.")
                } else {
                    Text("El modelo local ha sido copiado y está listo para usar.")
                }
            }
        }
    }
    
    // MARK: - Secciones de UI
    
    var huggingFaceSection: some View {
        Group {
            Section(header: Text("ID del Modelo")) {
                VStack(alignment: .leading, spacing: 8) {
                    TextField("Ej: mlx-community/Llama-3.2-1B-4bit", text: $modelID)
                        #if os(iOS)
                        .textInputAutocapitalization(.never)
                        #endif
                        .autocorrectionDisabled()
                    
                    Text("Ingresa el ID completo del modelo desde Hugging Face")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Label("Formato MLX", systemImage: "cube.box")
                    Text("El modelo debe estar en formato MLX (safetensors + config.json)")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    Label("Hugging Face Hub", systemImage: "cloud")
                    Text("Debe estar publicado en huggingface.co con formato: organización/nombre-modelo")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            } header: {
                Text("Requisitos")
            }
            
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Text("• mlx-community/Llama-3.2-1B-4bit")
                    Text("• mlx-community/SmolVLM-Instruct-4bit")
                    Text("• mlx-community/Qwen2.5-3B-4bit")
                }
                .font(.caption)
                .foregroundColor(.secondary)
            } header: {
                Text("Ejemplos de IDs Válidos")
            }
        }
    }
    
    var localStorageSection: some View {
        Group {
            Section(header: Text("Carpeta del Modelo")) {
                VStack(alignment: .leading, spacing: 12) {
                    if let folderURL = selectedFolderURL {
                        HStack {
                            Image(systemName: "folder.fill")
                                .foregroundColor(.blue)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(folderURL.lastPathComponent)
                                    .font(.headline)
                                Text(folderURL.path)
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                                    .lineLimit(2)
                            }
                        }
                        .padding(.vertical, 8)
                    } else {
                        Button {
                            showDocumentPicker = true
                        } label: {
                            HStack {
                                Image(systemName: "folder.badge.plus")
                                    .font(.title2)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Seleccionar Carpeta del Modelo")
                                        .font(.headline)
                                    Text("Toca para buscar en tu dispositivo")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                            .padding(.vertical, 12)
                        }
                    }
                }
            }
            
            Section {
                VStack(alignment: .leading, spacing: 12) {
                    Label("Estructura Requerida", systemImage: "doc.text.fill")
                    Text("La carpeta debe contener:")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.caption)
                            Text("config.json")
                                .font(.caption)
                        }
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.caption)
                            Text("*.safetensors (pesos del modelo)")
                                .font(.caption)
                        }
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.caption)
                            Text("tokenizer.json")
                                .font(.caption)
                        }
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                                .font(.caption)
                            Text("tokenizer_config.json")
                                .font(.caption)
                        }
                    }
                    .padding(.leading, 8)
                }
            } header: {
                Text("Archivos Necesarios")
            }
            
            Section {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Ubicaciones Comunes", systemImage: "info.circle")
                    Text("• Archivos (iCloud Drive)")
                    Text("• En Mi iPhone/iPad")
                    Text("• Carpetas compartidas")
                    Text("• Descargas")
                }
                .font(.caption)
                .foregroundColor(.secondary)
            } header: {
                Text("¿Dónde buscar?")
            }
        }
    }
    
    // MARK: - Funciones de Importación
    
    private func importModelFromHuggingFace() {
        // Validar formato básico
        guard modelID.contains("/") else {
            errorMessage = "Formato inválido. Debe ser: organización/nombre-modelo"
            return
        }
        
        // Limpiar espacios
        let cleanedID = modelID.trimmingCharacters(in: .whitespaces)
        
        // Validar que no esté vacío después de limpiar
        guard !cleanedID.isEmpty else {
            errorMessage = "El ID del modelo no puede estar vacío"
            return
        }
        
        // Verificar si ya existe
        if appManager.customModels.contains(cleanedID) {
            errorMessage = "Este modelo ya fue importado"
            return
        }
        
        // Agregar a modelos personalizados
        appManager.addCustomModel(cleanedID)
        errorMessage = nil
        showSuccess = true
    }
    
    private func importModelFromLocalStorage() {
        guard let folderURL = selectedFolderURL else {
            errorMessage = "No se seleccionó ninguna carpeta"
            return
        }
        
        // Verificar que la carpeta contenga los archivos necesarios
        let fileManager = FileManager.default
        
        // Verificar config.json
        let configURL = folderURL.appendingPathComponent("config.json")
        guard fileManager.fileExists(atPath: configURL.path) else {
            errorMessage = "La carpeta no contiene config.json"
            return
        }
        
        // Verificar tokenizer.json
        let tokenizerURL = folderURL.appendingPathComponent("tokenizer.json")
        guard fileManager.fileExists(atPath: tokenizerURL.path) else {
            errorMessage = "La carpeta no contiene tokenizer.json"
            return
        }
        
        // Buscar archivos .safetensors
        do {
            let contents = try fileManager.contentsOfDirectory(at: folderURL, includingPropertiesForKeys: nil)
            let hasSafetensors = contents.contains { $0.pathExtension == "safetensors" }
            
            guard hasSafetensors else {
                errorMessage = "La carpeta no contiene archivos .safetensors"
                return
            }
            
            // Copiar la carpeta del modelo a la ubicación de la app
            let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
            let modelsDirectory = documentsURL.appendingPathComponent("models", isDirectory: true)
            
            // Crear directorio de modelos si no existe
            if !fileManager.fileExists(atPath: modelsDirectory.path) {
                try fileManager.createDirectory(at: modelsDirectory, withIntermediateDirectories: true)
            }
            
            // Nombre del modelo desde la carpeta
            let modelName = folderURL.lastPathComponent
            let destinationURL = modelsDirectory.appendingPathComponent(modelName, isDirectory: true)
            
            // Si ya existe, eliminar primero
            if fileManager.fileExists(atPath: destinationURL.path) {
                try fileManager.removeItem(at: destinationURL)
            }
            
            // Copiar carpeta completa
            try fileManager.copyItem(at: folderURL, to: destinationURL)
            
            // Crear un ID local para el modelo
            let localModelID = "local/\(modelName)"
            
            // Agregar a modelos personalizados y marcar como instalado
            appManager.addCustomModel(localModelID)
            appManager.addInstalledModel(localModelID)
            
            errorMessage = nil
            showSuccess = true
            
        } catch {
            errorMessage = "Error al copiar el modelo: \(error.localizedDescription)"
        }
    }
}

// MARK: - Document Picker

#if os(iOS)
struct DocumentPicker: UIViewControllerRepresentable {
    @Binding var selectedURL: URL?
    var onImport: () -> Void
    
    func makeUIViewController(context: Context) -> UIDocumentPickerViewController {
        let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder])
        picker.allowsMultipleSelection = false
        picker.shouldShowFileExtensions = true
        picker.delegate = context.coordinator
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIDocumentPickerViewController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UIDocumentPickerDelegate {
        let parent: DocumentPicker
        
        init(_ parent: DocumentPicker) {
            self.parent = parent
        }
        
        func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
            guard let url = urls.first else { return }
            
            // Obtener acceso al archivo
            _ = url.startAccessingSecurityScopedResource()
            
            parent.selectedURL = url
            parent.onImport()
            
            // Liberar acceso
            url.stopAccessingSecurityScopedResource()
        }
    }
}
#endif

#Preview {
    ImportCustomModelView()
        .environmentObject(AppManager())
}
