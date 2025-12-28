//
//  LLMEvaluator.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/4/24.
//

import Foundation
import SwiftUI
import MLX
import MLXLLM
import MLXLMCommon
import MLXVLM
import MLXRandom
import CoreImage

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

#if os(iOS)
import UIKit
#endif

enum LLMEvaluatorError: Error {
    case modelNotFound(String)
}

@Observable
@MainActor
class LLMEvaluator {
    var running = false
    var cancelled = false
    var output = ""
    var modelInfo = ""
    var stat = ""
    var progress = 0.0
    var thinkingTime: TimeInterval?
    var collapsed: Bool = false
    var isThinking: Bool = false
    var currentModelContainer: ModelContainer?
    var memoryWarning: String?
    
    // MONITOR DE MEMORIA: Detecta presión de memoria en tiempo real
    private var memoryMonitorTimer: Timer?
    private var shouldStopDueToMemory = false

    var elapsedTime: TimeInterval? {
        if let startTime {
            return Date().timeIntervalSince(startTime)
        }

        return nil
    }

    private var startTime: Date?

    var modelConfiguration = ModelConfiguration.defaultModel

    func switchModel(_ model: ModelConfiguration) async {
        progress = 0.0 // reset progress
        loadState = .idle
        modelConfiguration = model
        _ = try? await load(modelName: model.name)
    }

    /// parameters controlling the output
    let generateParameters = GenerateParameters(temperature: 0.5)
    let maxTokens = 4096

    /// update the display every N tokens -- 4 looks like it updates continuously
    /// and is low overhead.  observed ~15% reduction in tokens/s when updating
    /// on every token
    let displayEveryNTokens = 4

    enum LoadState {
        case idle
        case loaded(ModelContainer)
    }

    var loadState = LoadState.idle

    /// load and return the model -- can be called multiple times, subsequent calls will
    /// just return the loaded model
    func load(modelName: String) async throws -> ModelContainer {
        // Intentar buscar modelo predefinido primero
        var model = ModelConfiguration.getModelByName(modelName)
        
        // Si no existe, podría ser un modelo personalizado
        if model == nil {
            model = ModelConfiguration.getModelByID(modelName)
        }
        
        guard let model = model else {
            throw LLMEvaluatorError.modelNotFound(modelName)
        }

        switch loadState {
        case .idle:
            // RESTRICCIONES ESTRICTAS: Limitar caché GPU agresivamente para 4GB RAM
            // iPhone 16e tiene ~4GB RAM total, dejamos solo 15MB para caché GPU
            MLX.GPU.set(cacheLimit: 15 * 1024 * 1024)
            
            // Liberar memoria antes de cargar
            MLX.GPU.clearCache()

            // Verificar si es un modelo local
            if modelName.hasPrefix("local/") {
                // Cargar desde almacenamiento local
                let modelContainer = try await loadLocalModel(model: model, modelName: modelName)
                modelInfo = "Loaded local model \(modelName). Weights: \(MLX.GPU.activeMemory / 1024 / 1024)M"
                loadState = .loaded(modelContainer)
                currentModelContainer = modelContainer
                return modelContainer
            } else {
                // Cargar desde Hugging Face
                // Use VLMModelFactory for vision models, LLMModelFactory for text-only models
                let factory: any ModelFactory = model.supportsVision ? VLMModelFactory.shared : LLMModelFactory.shared
                
                let modelContainer = try await factory.loadContainer(configuration: model) {
                    [modelConfiguration] progress in
                    Task { @MainActor in
                        self.modelInfo =
                            "Downloading \(modelConfiguration.name): \(Int(progress.fractionCompleted * 100))%"
                        self.progress = progress.fractionCompleted
                    }
                }
                modelInfo =
                    "Loaded \(modelConfiguration.id).  Weights: \(MLX.GPU.activeMemory / 1024 / 1024)M"
                loadState = .loaded(modelContainer)
                currentModelContainer = modelContainer
                return modelContainer
            }

        case let .loaded(modelContainer):
            currentModelContainer = modelContainer
            return modelContainer
        }
    }
    
    /// Carga un modelo desde el almacenamiento local del dispositivo
    private func loadLocalModel(model: ModelConfiguration, modelName: String) async throws -> ModelContainer {
        let fileManager = FileManager.default
        let documentsURL = fileManager.urls(for: .documentDirectory, in: .userDomainMask)[0]
        let modelsDirectory = documentsURL.appendingPathComponent("models", isDirectory: true)
        
        // Extraer nombre del modelo desde "local/nombre"
        let localModelName = modelName.replacingOccurrences(of: "local/", with: "")
        let modelURL = modelsDirectory.appendingPathComponent(localModelName, isDirectory: true)
        
        // Verificar que exista
        guard fileManager.fileExists(atPath: modelURL.path) else {
            throw LLMEvaluatorError.modelNotFound("Local model not found at: \(modelURL.path)")
        }
        
        // Determinar factory según tipo de modelo (por ahora asumimos LLM, puede mejorarse)
        let factory: any ModelFactory = model.supportsVision ? VLMModelFactory.shared : LLMModelFactory.shared
        
        // Cargar modelo desde URL local
        // MLX permite cargar modelos desde rutas locales usando la misma API
        let localConfig = ModelConfiguration(id: modelURL.path)
        
        let modelContainer = try await factory.loadContainer(configuration: localConfig) { progress in
            Task { @MainActor in
                self.modelInfo = "Loading local model: \(Int(progress.fractionCompleted * 100))%"
                self.progress = progress.fractionCompleted
            }
        }
        
        return modelContainer
    }

    func stop() {
        isThinking = false
        cancelled = true
        stopMemoryMonitoring()
    }
    
    // MONITOR DE MEMORIA: Verifica constantemente si hay presión de memoria
    private func startMemoryMonitoring() {
        shouldStopDueToMemory = false
        memoryWarning = nil
        
        memoryMonitorTimer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak self] _ in
            guard let self = self else { return }
            
            // Obtener memoria usada actual
            var info = mach_task_basic_info()
            var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size)/4
            let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) {
                $0.withMemoryRebound(to: integer_t.self, capacity: 1) {
                    task_info(mach_task_self_,
                             task_flavor_t(MACH_TASK_BASIC_INFO),
                             $0,
                             &count)
                }
            }
            
            if kerr == KERN_SUCCESS {
                let usedMemoryMB = Double(info.resident_size) / (1024 * 1024)
                let totalRAM = ProcessInfo.processInfo.physicalMemory
                let totalRAMGB = Double(totalRAM) / (1024 * 1024 * 1024)
                let usagePercent = (usedMemoryMB / 1024) / totalRAMGB
                
                // Si el uso de memoria es crítico (>85%), detener generación
                if usagePercent > 0.85 {
                    Task { @MainActor in
                        self.shouldStopDueToMemory = true
                        self.cancelled = true
                        self.memoryWarning = """
                        ⚠️ Generación detenida: Memoria crítica
                        
                        Uso: \(String(format: "%.1f", usagePercent * 100))% de \(String(format: "%.1f", totalRAMGB))GB RAM
                        
                        La conversación se detuvo para evitar que el dispositivo se reinicie.
                        
                        💡 Qué hacer:
                        1. Cierra otras apps en segundo plano
                        2. Reinicia la app (no el dispositivo)
                        3. Usa un modelo más pequeño:
                           • SmolVLM-Instruct-4bit (500MB)
                           • Llama-3.2-1B-4bit (700MB)
                        """
                        self.stopMemoryMonitoring()
                    }
                }
            }
        }
    }
    
    private func stopMemoryMonitoring() {
        memoryMonitorTimer?.invalidate()
        memoryMonitorTimer = nil
    }

    func generate(modelName: String, thread: Thread, systemPrompt: String, imageData: Data? = nil) async -> String {
        guard !running else { return "" }

        running = true
        cancelled = false
        output = ""
        startTime = Date()
        
        // MONITOREO DE MEMORIA: Iniciar vigilancia en tiempo real
        await MainActor.run {
            startMemoryMonitoring()
        }
        
        // RESTRICCIONES ESTRICTAS: Deshabilitar notificaciones e interrupciones durante inferencia
        #if os(iOS)
        await MainActor.run {
            UIApplication.shared.isIdleTimerDisabled = true // Prevenir sleep
        }
        #endif

        do {
            guard let model = ModelConfiguration.getModelByName(modelName) else {
                throw LLMEvaluatorError.modelNotFound(modelName)
            }
            
            // Check if this is a vision request with image data
            let isVisionRequest = model.supportsVision && imageData != nil
            
            #if os(iOS)
            if isVisionRequest, let imageData = imageData, let uiImage = UIImage(data: imageData) {
                // Use VLM for vision-language generation
                let result = try await generateWithVision(modelName: modelName, thread: thread, systemPrompt: systemPrompt, image: uiImage)
                running = false
                isThinking = false
                return result
            }
            #elseif os(macOS)
            if isVisionRequest, let imageData = imageData, let nsImage = NSImage(data: imageData) {
                // Use VLM for vision-language generation
                let result = try await generateWithVision(modelName: modelName, thread: thread, systemPrompt: systemPrompt, image: nsImage)
                running = false
                isThinking = false
                return result
            }
            #endif
            
            // Standard text-only generation
            let modelContainer = try await load(modelName: modelName)
            
            // Get configuration properties
            let configuration = await modelContainer.perform { context in
                context.configuration
            }

            // augment the prompt as needed
            let promptHistory = configuration.getPromptHistory(thread: thread, systemPrompt: systemPrompt)

            if configuration.modelType == .reasoning {
                isThinking = true
            }

            // each time you generate you will get something new
            MLXRandom.seed(UInt64(Date.timeIntervalSinceReferenceDate * 1000))

            let result = try await modelContainer.perform { context in
                // Standard text-only input
                let input = try await context.processor.prepare(input: .init(messages: promptHistory))
                
                return try MLXLMCommon.generate(
                    input: input, parameters: generateParameters, context: context
                ) { tokens in

                    var cancelled = false
                    var memoryStop = false
                    Task { @MainActor in
                        cancelled = self.cancelled
                        memoryStop = self.shouldStopDueToMemory
                    }

                    // update the output -- this will make the view show the text as it generates
                    if tokens.count % displayEveryNTokens == 0 {
                        let text = context.tokenizer.decode(tokens: tokens)
                        Task { @MainActor in
                            self.output = text
                        }
                    }

                    // Detener si hay presión de memoria, límite de tokens o cancelación manual
                    if tokens.count >= maxTokens || cancelled || memoryStop {
                        return .stop
                    } else {
                        return .more
                    }
                }
            }

            // update the text if needed, e.g. we haven't displayed because of displayEveryNTokens
            if result.output != output {
                output = result.output
            }
            stat = " Tokens/second: \(String(format: "%.3f", result.tokensPerSecond))"

        } catch {
            output = "Failed: \(error)"
        }

        // Detener monitoreo de memoria
        await MainActor.run {
            stopMemoryMonitoring()
        }
        
        // Restaurar configuración normal después de inferencia
        #if os(iOS)
        await MainActor.run {
            UIApplication.shared.isIdleTimerDisabled = false
        }
        #endif
        
        running = false
        isThinking = false // Reset thinking state when generation completes
        return output
    }
    
    /// Generate with vision-language model (iOS)
    #if os(iOS)
    private func generateWithVision(modelName: String, thread: Thread, systemPrompt: String, image: UIImage) async throws -> String {
        // MONITOREO DE MEMORIA: Iniciar vigilancia en tiempo real para modelos VLM
        await MainActor.run {
            startMemoryMonitoring()
        }
        
        do {
            let modelContainer = try await load(modelName: modelName)
        
        // Get configuration
        let configuration = await modelContainer.perform { context in
            context.configuration
        }
        
        // Build prompt history and get the last user message
        let promptHistory = configuration.getPromptHistory(thread: thread, systemPrompt: systemPrompt)
        guard let lastMessage = promptHistory.last, lastMessage["role"] == "user" else {
            throw LLMEvaluatorError.modelNotFound("No user message found")
        }
        
        let userPrompt = lastMessage["content"] ?? ""
        
        // Each time you generate you will get something new
        MLXRandom.seed(UInt64(Date.timeIntervalSinceReferenceDate * 1000))
        
        let result = try await modelContainer.perform { context in
            // Prepare VLM input with image using UserInput
            // Save image temporarily to pass as URL (required by UserInput.Image API)
            let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".jpg")
            if let imageData = image.jpegData(compressionQuality: 0.8) {
                try? imageData.write(to: tempURL)
            }
            
            let input = try await context.processor.prepare(
                input: .init(
                    prompt: .text(userPrompt),
                    images: [.url(tempURL)]
                )
            )
            
            return try MLXLMCommon.generate(
                input: input,
                parameters: generateParameters,
                context: context
            ) { tokens in
                var cancelled = false
                var memoryStop = false
                Task { @MainActor in
                    cancelled = self.cancelled
                    memoryStop = self.shouldStopDueToMemory
                }
                
                // Update the output
                if tokens.count % displayEveryNTokens == 0 {
                    let text = context.tokenizer.decode(tokens: tokens)
                    Task { @MainActor in
                        self.output = text
                    }
                }
                
                // Detener si hay presión de memoria, límite de tokens o cancelación manual
                if tokens.count >= maxTokens || cancelled || memoryStop {
                    return .stop
                } else {
                    return .more
                }
            }
        }
        
            // Update the text if needed
            if result.output != output {
                output = result.output
            }
            stat = " Tokens/second: \(String(format: "%.3f", result.tokensPerSecond))"
            
            // Detener monitoreo de memoria
            await MainActor.run {
                stopMemoryMonitoring()
            }
            
            return result.output
            
        } catch {
            // Detener monitoreo de memoria en caso de error
            await MainActor.run {
                stopMemoryMonitoring()
            }
            
            // Restaurar configuración incluso en caso de error
            #if os(iOS)
            await MainActor.run {
                UIApplication.shared.isIdleTimerDisabled = false
            }
            #endif
            
            // Provide helpful error message for common issues
            let errorMsg = error.localizedDescription.lowercased()
            if errorMsg.contains("unsupported") || errorMsg.contains("model type") {
                return """
                ❌ Error: Modelo no compatible con visión
                
                Modelos VLM OPTIMIZADOS para 4GB RAM:
                
                🎯 MEJOR OPCIÓN:
                • SmolVLM-Instruct-4bit (500MB) ⚡
                  Súper ligero, ideal para iPhone 16e
                
                • PaliGemma-3B-8bit (800MB)
                  Alternativa rápida y estable
                
                📥 Descarga: Perfil → ⚙️ Ajustes → Modelos
                
                ⚠️ Evita modelos >1GB si tienes 4GB RAM
                """
            } else if errorMsg.contains("memory") || errorMsg.contains("resource") {
                return """
                ❌ Error: Memoria insuficiente
                
                Tu dispositivo no tiene suficiente RAM para este modelo.
                
                Soluciones:
                1. Cierra otras apps
                2. Usa SmolVLM-Instruct-4bit (500MB)
                3. Reinicia el dispositivo
                
                Modelos ligeros recomendados:
                • SmolVLM-Instruct-4bit ⚡
                • Llama-3.2-1B-4bit
                """
            } else {
                return "❌ Error: \(error.localizedDescription)\n\nVerifica que el modelo esté correctamente instalado."
            }
        }
    }
    #endif
    
    #if os(macOS)
    /// Generate with vision-language model (macOS)
    private func generateWithVision(modelName: String, thread: Thread, systemPrompt: String, image: NSImage) async throws -> String {
        // Simplemente retornar un mensaje ya que VLM puede no estar optimizado para macOS
        return "Vision models are currently optimized for iOS devices only."
    }
    #endif
}
