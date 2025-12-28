//
//  Models.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/4/24.
//

import Foundation
import MLXLMCommon

public extension ModelConfiguration {
    enum ModelType {
        case regular, reasoning
    }
    
    enum ModelCategory {
        case reasoning      // Modelos de razonamiento (DeepSeek-R1, Qwen con reasoning)
        case multimodal     // Modelos de visión (SmolVLM, PaliGemma, Qwen2-VL)
        case text           // Modelos de texto estándar (Llama, Qwen básicos)
        case imported       // Modelos importados por usuario
        case unknown        // Desconocidos
    }

    var modelType: ModelType {
        switch self {
        case .deepseek_r1_distill_qwen_1_5b_4bit: .reasoning
        case .deepseek_r1_distill_qwen_1_5b_8bit: .reasoning
        case .qwen_3_4b_4bit: .reasoning
        case .qwen_3_8b_4bit: .reasoning
        default: .regular
        }
    }
    
    var category: ModelCategory {
        switch self {
        // Modelos de razonamiento
        case .deepseek_r1_distill_qwen_1_5b_4bit: return .reasoning
        case .deepseek_r1_distill_qwen_1_5b_8bit: return .reasoning
        case .qwen_3_4b_4bit: return .reasoning
        case .qwen_3_8b_4bit: return .reasoning
            
        // Modelos multimodales
        case .smolvlm_instruct_4bit: return .multimodal
        case .paligemma_3b_8bit: return .multimodal
            
        // Modelos de texto
        case .llama_3_2_1b_4bit: return .text
        case .llama_3_2_3b_4bit: return .text
            
        default:
            // Para modelos personalizados, detectar por nombre o características
            let lowerName = name.lowercased()
            
            // Modelos locales importados por usuario
            if lowerName.hasPrefix("local/") {
                return .imported
            }
            
            // Detectar razonamiento por nombre
            if lowerName.contains("deepseek") && lowerName.contains("r1") {
                return .reasoning
            }
            if lowerName.contains("qwen") && (lowerName.contains("reasoning") || lowerName.contains("qwen3") || lowerName.contains("qwen-3")) {
                return .reasoning
            }
            
            // Detectar multimodal por nombre o propiedad
            if supportsVision {
                return .multimodal
            }
            if lowerName.contains("vl") || lowerName.contains("vision") || lowerName.contains("vlm") {
                return .multimodal
            }
            if lowerName.contains("smolvlm") || lowerName.contains("paligemma") || lowerName.contains("llava") {
                return .multimodal
            }
            
            // Detectar si es modelo estándar de texto
            if lowerName.contains("llama") || lowerName.contains("mistral") || lowerName.contains("phi") || lowerName.contains("gemma") {
                return .text
            }
            
            // Por defecto, si no se puede determinar
            return .unknown
        }
    }
    
    var categoryDisplayName: String {
        switch category {
        case .reasoning: return "🧠 Razonamiento"
        case .multimodal: return "👁️ Multimodal"
        case .text: return "💬 Texto"
        case .imported: return "📦 Importados"
        case .unknown: return "❓ Otros"
        }
    }
    
    var categoryIcon: String {
        switch category {
        case .reasoning: return "brain.head.profile"
        case .multimodal: return "eye.fill"
        case .text: return "text.bubble.fill"
        case .imported: return "square.and.arrow.down.fill"
        case .unknown: return "questionmark.circle.fill"
        }
    }
    
    var categoryDescription: String {
        switch category {
        case .reasoning: return "Modelos especializados en razonamiento complejo y pensamiento paso a paso"
        case .multimodal: return "Modelos que pueden analizar imágenes y responder preguntas sobre ellas"
        case .text: return "Modelos de conversación y generación de texto estándar"
        case .imported: return "Modelos personalizados importados por ti"
        case .unknown: return "Modelos de tipo desconocido o no clasificado"
        }
    }
}

extension ModelConfiguration {

    public static let llama_3_2_1b_4bit = ModelConfiguration(
        id: "mlx-community/Llama-3.2-1B-Instruct-4bit"
    )

    public static let llama_3_2_3b_4bit = ModelConfiguration(
        id: "mlx-community/Llama-3.2-3B-Instruct-4bit"
    )

    public static let deepseek_r1_distill_qwen_1_5b_4bit = ModelConfiguration(
        id: "mlx-community/DeepSeek-R1-Distill-Qwen-1.5B-4bit"
    )

    public static let deepseek_r1_distill_qwen_1_5b_8bit = ModelConfiguration(
        id: "mlx-community/DeepSeek-R1-Distill-Qwen-1.5B-8bit"
    )

    public static let qwen_3_4b_4bit = ModelConfiguration(
        id: "mlx-community/Qwen3-4B-4bit"
    )

    public static let qwen_3_8b_4bit = ModelConfiguration(
        id: "mlx-community/Qwen3-8B-4bit"
    )

    public static let smolvlm_instruct_4bit = ModelConfiguration(
        id: "mlx-community/SmolVLM-Instruct-4bit"
    )
    
    public static let paligemma_3b_8bit = ModelConfiguration(
        id: "mlx-community/paligemma-3b-mix-448-8bit"
    )

    public static var availableModels: [ModelConfiguration] = [
        llama_3_2_1b_4bit,
        llama_3_2_3b_4bit,
        deepseek_r1_distill_qwen_1_5b_4bit,
        deepseek_r1_distill_qwen_1_5b_8bit,
        qwen_3_4b_4bit,
        qwen_3_8b_4bit,
        smolvlm_instruct_4bit,
        paligemma_3b_8bit,
    ]

    public static var defaultModel: ModelConfiguration {
        llama_3_2_1b_4bit
    }

    /// Obtiene todos los modelos disponibles incluyendo personalizados
    public static func getAllAvailableModels(customModels: [String]) -> [ModelConfiguration] {
        var allModels = availableModels
        
        // Agregar modelos personalizados dinámicamente
        for customID in customModels {
            let customModel = ModelConfiguration(id: customID)
            allModels.append(customModel)
        }
        
        return allModels
    }
    
    /// Agrupa modelos por categoría
    public static func groupModelsByCategory(_ models: [ModelConfiguration]) -> [ModelCategory: [ModelConfiguration]] {
        var grouped: [ModelCategory: [ModelConfiguration]] = [:]
        
        for model in models {
            let category = model.category
            if grouped[category] == nil {
                grouped[category] = []
            }
            grouped[category]?.append(model)
        }
        
        return grouped
    }
    
    /// Orden de categorías para mostrar
    public static var categoryOrder: [ModelCategory] {
        return [.reasoning, .multimodal, .text, .imported, .unknown]
    }

    public static func getModelByName(_ name: String) -> ModelConfiguration? {
        if let model = availableModels.first(where: { $0.name == name }) {
            return model
        } else {
            return nil
        }
    }
    
    /// Crea un ModelConfiguration desde un ID de Hugging Face (dinámico)
    public static func getModelByID(_ id: String) -> ModelConfiguration {
        // Primero buscar en modelos predefinidos comparando el nombre
        // El nombre se deriva del id (ej: "mlx-community/Llama-3.2-1B-4bit" -> nombre similar)
        let tempModel = ModelConfiguration(id: id)
        
        if let existingModel = availableModels.first(where: { $0.name == tempModel.name }) {
            return existingModel
        }
        
        // Si no existe, retornar el modelo temporal creado
        return tempModel
    }

    func getPromptHistory(thread: Thread, systemPrompt: String) -> [[String: String]] {
        var history: [[String: String]] = []

        // system prompt
        history.append([
            "role": "system",
            "content": systemPrompt,
        ])

        // messages
        for message in thread.sortedMessages {
            let role = message.role.rawValue
            history.append([
                "role": role,
                "content": formatForTokenizer(message.content), // remove reasoning part
            ])
        }

        return history
    }

    // TODO: Remove this function when Jinja gets updated
    func formatForTokenizer(_ message: String) -> String {
        if modelType == .reasoning {
            let pattern = "<think>.*?(</think>|$)"
            do {
                let regex = try NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators])
                let range = NSRange(location: 0, length: message.utf16.count)
                let formattedMessage = regex.stringByReplacingMatches(in: message, options: [], range: range, withTemplate: "")
                return " " + formattedMessage
            } catch {
                return " " + message
            }
        }
        return message
    }

    /// Returns the model's approximate size, in GB.
    public var modelSize: Decimal? {
        switch self {
        case .llama_3_2_1b_4bit: return 0.7
        case .llama_3_2_3b_4bit: return 1.8
        case .deepseek_r1_distill_qwen_1_5b_4bit: return 1.0
        case .deepseek_r1_distill_qwen_1_5b_8bit: return 1.9
        case .qwen_3_4b_4bit: return 2.3
        case .qwen_3_8b_4bit: return 4.7
        case .smolvlm_instruct_4bit: return 0.5
        case .paligemma_3b_8bit: return 0.8
        default: return nil
        }
    }
    
    /// Returns true if the model supports vision/multimodal capabilities
    public var supportsVision: Bool {
        switch self {
        case .smolvlm_instruct_4bit: return true
        case .paligemma_3b_8bit: return true
        default: return false
        }
    }
}
