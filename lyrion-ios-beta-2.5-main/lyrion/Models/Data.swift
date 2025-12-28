//
//  Data.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/5/24.
//

import SwiftUI
import SwiftData

class AppManager: ObservableObject {
    @AppStorage("systemPrompt") var systemPrompt = """
You are a helpful assistant. When explaining mathematical, scientific, chemical, or physical concepts, ALWAYS use LaTeX formatting:
- Use inline LaTeX with single $ for variables and short expressions: $ x $, $ F = ma $, $ E = mc^2 $
- Use display LaTeX with double $$ for complex formulas and equations
- For chemistry, use LaTeX: $ \\text{H}_2\\text{O} $, $ \\text{CO}_2 $
- Always format mathematical expressions, equations, chemical formulas, and physics equations in LaTeX
- Use proper LaTeX syntax: subscripts with _, superscripts with ^, fractions with \\frac{}{}, Greek letters, etc.

Examples:
- Force: $ F = G \\cdot \\frac{m_1 \\cdot m_2}{r^2} $
- Chemistry: $ 2\\text{H}_2 + \\text{O}_2 \\rightarrow 2\\text{H}_2\\text{O} $
- Energy: $$ E = mc^2 $$
"""
    @AppStorage("appTintColor") var appTintColor: AppTintColor = .monochrome
    @AppStorage("appFontDesign") var appFontDesign: AppFontDesign = .standard
    @AppStorage("appFontSize") var appFontSize: AppFontSize = .medium
    @AppStorage("appFontWidth") var appFontWidth: AppFontWidth = .standard
    @AppStorage("currentModelName") var currentModelName: String?
    @AppStorage("shouldPlayHaptics") var shouldPlayHaptics = true
    @AppStorage("numberOfVisits") var numberOfVisits = 0
    @AppStorage("numberOfVisitsOfLastRequest") var numberOfVisitsOfLastRequest = 0
    @AppStorage("appColorScheme") var appColorScheme: AppColorScheme = .system
    @AppStorage("ttsRate") var ttsRate: Double = 0.5
    @AppStorage("ttsPitch") var ttsPitch: Double = 1.0
    @AppStorage("ttsVolume") var ttsVolume: Double = 1.0
    @AppStorage("ttsVoiceIdentifier") var ttsVoiceIdentifier: String = ""
    @AppStorage("homeModelName") var homeModelName: String = ""
    @Published var isFullScreenMode: Bool = false
    
    var userInterfaceIdiom: LayoutType {
        #if os(visionOS)
        return .vision
        #elseif os(macOS)
        return .mac
        #elseif os(iOS)
        return UIDevice.current.userInterfaceIdiom == .pad ? .pad : .phone
        #else
        return .unknown
        #endif
    }
    
    var availableMemory: Double {
        let ramInBytes = ProcessInfo.processInfo.physicalMemory
        let ramInGB = Double(ramInBytes) / (1024 * 1024 * 1024)
        return ramInGB
    }

    enum LayoutType {
        case mac, phone, pad, vision, unknown
    }
        
    private let installedModelsKey = "installedModels"
    private let customModelsKey = "customModels"
        
    @Published var installedModels: [String] = [] {
        didSet {
            saveInstalledModelsToUserDefaults()
        }
    }
    
    @Published var customModels: [String] = [] {
        didSet {
            saveCustomModelsToUserDefaults()
        }
    }
    
    init() {
        loadInstalledModelsFromUserDefaults()
        loadCustomModelsFromUserDefaults()
    }
    
    func incrementNumberOfVisits() {
        numberOfVisits += 1
        print("app visits: \(numberOfVisits)")
    }
    
    // Function to save the array to UserDefaults as JSON
    private func saveInstalledModelsToUserDefaults() {
        if let jsonData = try? JSONEncoder().encode(installedModels) {
            UserDefaults.standard.set(jsonData, forKey: installedModelsKey)
        }
    }
    
    // Function to load the array from UserDefaults
    private func loadInstalledModelsFromUserDefaults() {
        if let jsonData = UserDefaults.standard.data(forKey: installedModelsKey),
           let decodedArray = try? JSONDecoder().decode([String].self, from: jsonData) {
            self.installedModels = decodedArray
        } else {
            self.installedModels = [] // Default to an empty array if there's no data
        }
    }
    
    func playHaptic() {
        if shouldPlayHaptics {
            #if os(iOS)
            let impact = UIImpactFeedbackGenerator(style: .soft)
            impact.impactOccurred()
            #endif
        }
    }
    
    // Function to save custom models to UserDefaults
    private func saveCustomModelsToUserDefaults() {
        if let jsonData = try? JSONEncoder().encode(customModels) {
            UserDefaults.standard.set(jsonData, forKey: customModelsKey)
        }
    }
    
    // Function to load custom models from UserDefaults
    private func loadCustomModelsFromUserDefaults() {
        if let jsonData = UserDefaults.standard.data(forKey: customModelsKey),
           let decodedArray = try? JSONDecoder().decode([String].self, from: jsonData) {
            self.customModels = decodedArray
        } else {
            self.customModels = []
        }
    }
    
    func addInstalledModel(_ model: String) {
        if !installedModels.contains(model) {
            installedModels.append(model)
        }
    }
    
    func addCustomModel(_ modelID: String) {
        if !customModels.contains(modelID) {
            customModels.append(modelID)
        }
    }
    
    func removeCustomModel(_ modelID: String) {
        customModels.removeAll { $0 == modelID }
        // También remover de instalados si existe
        installedModels.removeAll { $0.contains(modelID) }
    }
    
    func modelDisplayName(_ modelName: String) -> String {
        return modelName.replacingOccurrences(of: "mlx-community/", with: "").lowercased()
    }
    
    func getMoonPhaseIcon() -> String {
        // Return Lyrion icon instead of moon phases
        return "brain.head.profile"
    }
    
    // MARK: - Attribute Management
    @MainActor
    func resetAndInitializeAttributes(modelContext: ModelContext) {
        // Verificar si ya existen atributos
        let descriptor = FetchDescriptor<UserAttribute>()
        do {
            let count = try modelContext.fetchCount(descriptor)
            if count > 0 {
                print("✅ Atributos ya existen (\(count)), no se reiniciarán.")
                return
            }
        } catch {
            print("Error checking attributes: \(error)")
        }
        
        // Si no existen, crearlos
        print("⚠️ No se encontraron atributos, inicializando por defecto...")
        
        // 2. Definir los atributos por defecto
        let defaultAttributes: [(category: String, name: String, description: String, order: Int)] = [
            // Físico
            ("Físico", "Fuerza", "Potencia muscular total.", 0),
            ("Físico", "Resistencia", "Aguante físico general.", 1),
            ("Físico", "Velocidad", "Rapidez de movimiento.", 2),
            ("Físico", "Agilidad", "Flexibilidad y equilibrio.", 3),
            ("Físico", "Coordinación", "Precisión de movimientos.", 4),
            ("Físico", "Vitalidad", "Salud y robustez.", 5),
            ("Físico", "Destreza", "Habilidad manual fina.", 6),
            ("Físico", "Kinestesia", "Conciencia corporal.", 7),
            ("Físico", "Armadura natural", "Resistencia física.", 8),
            ("Físico", "Reflejos", "Tiempo de reacción.", 9),
            ("Físico", "Stamina", "Energía de acción.", 10),
            
            // Mental
            ("Mental", "Inteligencia", "Aprendizaje y lógica.", 11),
            ("Mental", "Percepción", "Notar detalles.", 12),
            ("Mental", "Voluntad", "Resistencia mental.", 13),
            ("Mental", "Creatividad", "Imaginación.", 14),
            ("Mental", "Atención", "Concentración.", 15),
            ("Mental", "Conocimiento técnico", "Saber especializado.", 16),
            ("Mental", "Medicina", "Curación y diagnóstico.", 17),
            ("Mental", "Visión", "Claridad visual.", 18),
            ("Mental", "Audición", "Sensibilidad auditiva.", 19),
            ("Mental", "Olfato / Gusto", "Sentidos químicos.", 20),
            ("Mental", "Consciencia", "Entendimiento profundo.", 21),
            ("Mental", "Control interno", "Autocontrol.", 22),
            
            // Social
            ("Social", "Carisma", "Influencia personal.", 23),
            ("Social", "Estabilidad emocional", "Control de emociones.", 24),
            ("Social", "Empatía", "Entender a otros.", 25),
            ("Social", "Confianza", "Seguridad.", 26),
            ("Social", "Reputación", "Fama o estatus.", 27),
            ("Social", "Influencia", "Poder sobre grupos.", 28),
            ("Social", "Negociación", "Tratos y comercio.", 29),
            ("Social", "Liderazgo", "Dirigir a otros.", 30),
            
            // Otros
            ("Otros", "Supervivencia", "Subsistencia natural.", 31),
            ("Otros", "Defensa mágica", "Protección sobrenatural.", 32),
            ("Otros", "Mana", "Energía mágica.", 33),
            ("Otros", "Aura", "Presencia energética.", 34),
            ("Otros", "Suerte", "Probabilidad favorable.", 35),
            ("Otros", "Afinidad elemental", "Conexión elemental.", 36),
            ("Otros", "Adaptación", "Ajuste al entorno.", 37),
            ("Otros", "Habilidad única", "Don especial.", 38),
            ("Otros", "Moralidad", "Alineación ética.", 39),
            ("Otros", "Ambición", "Deseo de poder.", 40),
            ("Otros", "Lealtad", "Fidelidad.", 41)
        ]
        
        // 3. Crear e insertar nuevos atributos (valor inicial 0.0)
        for attr in defaultAttributes {
            let newAttribute = UserAttribute(
                category: attr.category,
                name: attr.name,
                descriptionText: attr.description,
                value: 0.0, // Empieza en 0 como solicitado
                averageValue: 5.0, // Promedio estándar
                order: attr.order
            )
            modelContext.insert(newAttribute)
        }
        
        print("✅ Atributos inicializados correctamente.")
    }
}

enum Role: String, Codable {
    case assistant
    case user
    case system
}

@Model
class Message {
    @Attribute(.unique) var id: UUID
    var role: Role
    var content: String
    var timestamp: Date
    var generatingTime: TimeInterval?
    @Attribute(.externalStorage) var imageData: Data?
    
    @Relationship(inverse: \Thread.messages) var thread: Thread?
    
    init(role: Role, content: String, thread: Thread? = nil, generatingTime: TimeInterval? = nil, imageData: Data? = nil) {
        self.id = UUID()
        self.role = role
        self.content = content
        self.timestamp = Date()
        self.thread = thread
        self.generatingTime = generatingTime
        self.imageData = imageData
    }
}

@Model
final class Thread {
    @Attribute(.unique) var id: UUID
    var title: String?
    var timestamp: Date
    var customSystemPrompt: String?
    
    @Relationship var messages: [Message] = []
    
    var sortedMessages: [Message] {
        return messages.sorted { $0.timestamp < $1.timestamp }
    }
    
    init() {
        self.id = UUID()
        self.timestamp = Date()
        self.customSystemPrompt = nil
    }
}

extension Thread: @unchecked Sendable {}
extension Message: @unchecked Sendable {}

enum AppTintColor: String, CaseIterable {
    case monochrome, blue, brown, gray, green, indigo, mint, orange, pink, purple, red, teal, yellow
    
    func getColor() -> Color {
        switch self {
        case .monochrome:
            .primary
        case .blue:
            .blue
        case .red:
            .red
        case .green:
            .green
        case .yellow:
            .yellow
        case .brown:
            .brown
        case .gray:
            .gray
        case .indigo:
            .indigo
        case .mint:
            .mint
        case .orange:
            .orange
        case .pink:
            .pink
        case .purple:
            .purple
        case .teal:
            .teal
        }
    }
}

enum AppFontDesign: String, CaseIterable {
    case standard, monospaced, rounded, serif
    
    func getFontDesign() -> Font.Design {
        switch self {
        case .standard:
            .default
        case .monospaced:
            .monospaced
        case .rounded:
            .rounded
        case .serif:
            .serif
        }
    }
}

enum AppFontWidth: String, CaseIterable {
    case compressed, condensed, expanded, standard
    
    func getFontWidth() -> Font.Width {
        switch self {
        case .compressed:
            .compressed
        case .condensed:
            .condensed
        case .expanded:
            .expanded
        case .standard:
            .standard
        }
    }
}

enum AppFontSize: String, CaseIterable {
    case xsmall, small, medium, large, xlarge
    
    func getFontSize() -> DynamicTypeSize {
        switch self {
        case .xsmall:
            .xSmall
        case .small:
            .small
        case .medium:
            .medium
        case .large:
            .large
        case .xlarge:
            .xLarge
        }
    }
}

enum AppColorScheme: String, CaseIterable {
    case system, light, dark
    
    var colorScheme: ColorScheme? {
        switch self {
        case .system:
            return nil
        case .light:
            return .light
        case .dark:
            return .dark
        }
    }
    
    var displayName: String {
        switch self {
        case .system:
            return NSLocalizedString("appearance.system", comment: "")
        case .light:
            return NSLocalizedString("appearance.light", comment: "")
        case .dark:
            return NSLocalizedString("appearance.dark", comment: "")
        }
    }
}
