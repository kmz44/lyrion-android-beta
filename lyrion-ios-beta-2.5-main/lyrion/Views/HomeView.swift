//
//  HomeView.swift
//  Lyrion
//
//
//

import SwiftUI
import SwiftData
import AVFoundation
import Speech

enum HomeViewState {
    case main
    case categories
    case stats(String, page: Int) // Changed StatCategory to String (category name)
}

struct HomeView: View {
    @EnvironmentObject var appManager: AppManager
    @Environment(LLMEvaluator.self) var llm
    @Query var userProfiles: [UserProfile]
    @Query(sort: \UserAttribute.order) var userAttributes: [UserAttribute]
    
    @State private var speechManager = SpeechManager()
    @State private var isProcessingVoice = false
    @State private var voiceResponse = ""
    @Environment(\.modelContext) var modelContext // Necesario para guardar mensajes
    
    @State private var viewState: HomeViewState = .main
    @State private var cameraPosition: AVCaptureDevice.Position = .back
    
    // Perfil actual para personalización
    private var currentProfile: UserProfile? {
        userProfiles.first
    }
    
    var body: some View {
        ZStack {
            // CAPA 1: Fondo de Cámara
            CameraBackground(cameraPosition: $cameraPosition)
                .blur(radius: 5)
                .ignoresSafeArea()
                .allowsHitTesting(false) // CRÍTICO: Evita que la cámara capture toques
            
            // CAPA 2: Interfaz de Usuario
            VStack(spacing: 0) {
                // Barra superior con controles de cámara y voz
                topControls
                    .padding(.top, 50)
                    .padding(.horizontal)
                
                Spacer()
                
                // Panel de Cristal Central
                glassPanel
                    .frame(maxWidth: 600)
                    .padding(.horizontal, 20)
                    // Altura máxima ajustada para evitar superposición con TabBar
                    .frame(maxHeight: 500) 
                    .padding(.bottom, 80) // Margen inferior extra para seguridad
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity) // Asegura que el VStack ocupe todo el espacio
        }
        .onAppear {
            print("✅ HomeView cargado correctamente")
            // Aseguramos estado inicial válido si es necesario
            if case .main = viewState {
                // Ya estamos en main, todo bien
            }
        }
        .onChange(of: speechManager.isRecording) { oldValue, newValue in
            // Cuando deja de grabar (newValue == false) y hay texto, procesar
            if !newValue && !speechManager.transcribedText.isEmpty {
                processVoiceInput(speechManager.transcribedText)
            }
        }
    }
    
    // MARK: - Helper para Opacidad Segura
    private func safeOpacity(_ value: Double?) -> Double {
        guard let val = value else { return 0.3 }
        if val.isNaN || val.isInfinite { return 0.3 }
        return max(0.0, min(1.0, val))
    }
    
    // MARK: - Controles Superiores
    private var topControls: some View {
        HStack {
            // Panel de Voz
            HStack(spacing: 12) {
                Button(action: {
                    toggleVoiceRecording()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: speechManager.isRecording ? "stop.circle.fill" : "mic.circle.fill")
                            .font(.title2)
                            .symbolEffect(.pulse, isActive: speechManager.isRecording)
                        
                        if speechManager.isRecording {
                            Text("Escuchando...")
                                .font(.caption)
                                .fontWeight(.bold)
                        } else if isProcessingVoice {
                            Text("Procesando...")
                                .font(.caption)
                                .fontWeight(.bold)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(speechManager.isRecording ? Color.red.opacity(0.8) : (isProcessingVoice ? Color.blue.opacity(0.8) : Color.black.opacity(0.3)))
                    .foregroundColor(.white)
                    .clipShape(Capsule())
                    .overlay(
                        Capsule()
                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                    )
                }
                .buttonStyle(ScaleButtonStyle())
                .disabled(isProcessingVoice) // Deshabilitar mientras procesa
            }
            
            Spacer()
            
            // Controles de Cámara
            HStack(spacing: 12) {
                cameraButton(title: "Trasera", icon: "camera.fill", position: .back)
                cameraButton(title: "Frontal", icon: "person.fill", position: .front)
            }
        }
    }
    
    private func cameraButton(title: String, icon: String, position: AVCaptureDevice.Position) -> some View {
        Button(action: {
            print("🔘 [HomeView] Button tapped: Cámara \(title)")
            cambiarCamara(position)
        }) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                Text(title)
                    .font(.caption)
                    .fontWeight(.bold)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(cameraPosition == position ? Color.white.opacity(0.3) : Color.black.opacity(0.3))
            .foregroundColor(.white)
            .clipShape(Capsule())
            .overlay(
                Capsule()
                    .stroke(Color.white.opacity(0.3), lineWidth: 1)
            )
        }
        .buttonStyle(ScaleButtonStyle())
    }
    
    private func cambiarCamara(_ position: AVCaptureDevice.Position) {
        if cameraPosition != position {
            appManager.playHaptic()
            withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                cameraPosition = position
            }
        }
    }
    
    private func toggleVoiceRecording() {
        appManager.playHaptic()
        if speechManager.isRecording {
            speechManager.stopRecording()
            // El procesamiento se activará en el onChange(of: isRecording)
        } else {
            // Detener TTS si está hablando
            if speechManager.isSpeaking {
                speechManager.stopSpeaking()
            }
            
            // Limpiar texto anterior
            speechManager.transcribedText = ""
            
            do {
                try speechManager.startRecording()
            } catch {
                print("Error al iniciar grabación: \(error)")
            }
        }
    }
    
    private func processVoiceInput(_ text: String) {
        guard !text.isEmpty else { return }
        
        // Verificar si hay un modelo seleccionado (ya sea específico de Home o global)
        let modelToUse = !appManager.homeModelName.isEmpty ? appManager.homeModelName : appManager.currentModelName
        
        guard let modelName = modelToUse, !modelName.isEmpty else {
            print("⚠️ No hay modelo seleccionado")
            speechManager.speak(
                "Por favor, selecciona un modelo de inteligencia artificial en tu perfil primero.",
                rate: Float(appManager.ttsRate),
                pitch: Float(appManager.ttsPitch),
                volume: Float(appManager.ttsVolume),
                voiceIdentifier: appManager.ttsVoiceIdentifier
            )
            return
        }
        
        isProcessingVoice = true
        
        Task {
            // 1. Crear o recuperar hilo de chat (opcional, aquí usaremos uno temporal o el último)
            // Para simplificar, usaremos el modelo directamente sin guardar historial visible en Home,
            // pero sí interactuando con el LLM.
            
            let tempThread = Thread() // Hilo temporal para el contexto de esta interacción
            
            // CRÍTICO: Agregar el mensaje del usuario al hilo para que el LLM sepa qué responder
            let userMessage = Message(role: .user, content: text)
            tempThread.messages.append(userMessage)
            
            // 2. Generar respuesta con LLM
            // Usamos un System Prompt específico para voz que anula las instrucciones de LaTeX/Matemáticas del global
            // para evitar que el modelo alucine con fórmulas en conversaciones casuales.
            let voiceSystemPrompt = """
            Eres un asistente de voz inteligente y conciso.
            - Responde SIEMPRE en español.
            - Tus respuestas deben ser MUY BREVES (máximo 2 oraciones).
            - Habla de manera natural y fluida.
            - NO uses fórmulas, LaTeX ni Markdown complejo, ya que tu respuesta será leída en voz alta.
            - Si el usuario te saluda, saluda brevemente. Si te pregunta algo simple, responde directo al grano.
            """
            
            let output = await llm.generate(modelName: modelName, thread: tempThread, systemPrompt: voiceSystemPrompt, imageData: nil)
            
            print("🎤 [Voz] Transcripción: \(text)")
            print("🤖 [IA] Respuesta: \(output)")
            
            // 3. Reproducir respuesta con TTS
            await MainActor.run {
                isProcessingVoice = false
                voiceResponse = output
                if !output.isEmpty {
                    speechManager.speak(
                        output,
                        rate: Float(appManager.ttsRate),
                        pitch: Float(appManager.ttsPitch),
                        volume: Float(appManager.ttsVolume),
                        voiceIdentifier: appManager.ttsVoiceIdentifier
                    )
                }
            }
        }
    }
    
    // MARK: - Panel de Cristal
    private var glassPanel: some View {
        ZStack {
            // Fondo Personalizable con Borde Coherente
            RoundedRectangle(cornerRadius: 30)
                // CAPA DE COLOR (Tinte/Polarizado) - Controlado por containerOpacity
                .fill(Color(hex: currentProfile?.containerColorHex ?? "#000000").opacity(safeOpacity(currentProfile?.containerOpacity)))
                // CAPA DE MATERIAL (Blur/Transparencia) - Controlado por containerAlpha
                .background(.ultraThinMaterial.opacity(safeOpacity(currentProfile?.containerAlpha)))
                .clipShape(RoundedRectangle(cornerRadius: 30))
                .overlay(
                    RoundedRectangle(cornerRadius: 30)
                        .stroke(Color(hex: currentProfile?.containerBorderColorHex ?? "#FFFFFF"), lineWidth: 1)
                )
                .shadow(color: .black.opacity(0.2), radius: 20, x: 0, y: 10)
            
            // Contenido del Panel
            VStack {
                switch viewState {
                case .main:
                    MainContentView(
                        profile: currentProfile,
                        onExplore: {
                            print("🔘 [HomeView] Button tapped: Explorar")
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                                viewState = .categories
                            }
                        }
                    )
                    .transition(.opacity.combined(with: .scale(scale: 0.95)))
                    
                case .categories:
                    CategoriesContentView(
                        profile: currentProfile,
                        attributes: userAttributes,
                        onBack: {
                            print("🔘 [HomeView] Button tapped: Volver (Categorías)")
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                                viewState = .main
                            }
                        },
                        onSelectCategory: { categoryName in
                            print("🔘 [HomeView] Button tapped: Categoría \(categoryName)")
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                                viewState = .stats(categoryName, page: 0)
                            }
                        }
                    )
                    .transition(.move(edge: .trailing))
                    
                case .stats(let categoryName, let page):
                    StatsContentView(
                        categoryName: categoryName,
                        attributes: userAttributes.filter { $0.category == categoryName },
                        page: page,
                        profile: currentProfile,
                        onBack: {
                            print("🔘 [HomeView] Button tapped: Volver (Estadísticas)")
                            withAnimation(.spring(response: 0.5, dampingFraction: 0.8)) {
                                viewState = .categories
                            }
                        },
                        onPageChange: { newPage in
                            print("🔘 [HomeView] Button tapped: Página \(newPage)")
                            withAnimation(.easeInOut(duration: 0.3)) {
                                viewState = .stats(categoryName, page: newPage)
                            }
                        }
                    )
                    .transition(.move(edge: .trailing))
                }
            }
            .padding(30)
        }
    }
}

// MARK: - Subvistas (Componentes)

struct MainContentView: View {
    let profile: UserProfile?
    let onExplore: () -> Void
    @EnvironmentObject var appManager: AppManager
    
    var body: some View {
        VStack(spacing: 25) {
            Spacer()
            
            Text("Hola, \(profile?.name ?? "Usuario")")
                .font(getFont(size: 42, weight: .bold))
                .foregroundColor(Color(hex: profile?.titleColorHex ?? "#FFFFFF"))
                .multilineTextAlignment(.center)
                .shadow(color: .black.opacity(0.1), radius: 2, x: 0, y: 1)
            
            Text("Bienvenido a tu experiencia de computación espacial.")
                .font(getFont(size: 18, weight: .regular))
                .foregroundColor(Color(hex: profile?.subtitleColorHex ?? "#E0E0E0"))
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            
            Spacer()
            
            Button(action: {
                appManager.playHaptic()
                onExplore()
            }) {
                Text("Explorar")
                    .font(getFont(size: 20, weight: .semibold))
                    .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF"))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 18)
                    .background(
                        Color(hex: profile?.buttonBackgroundColorHex ?? "#4A90E2")
                            .opacity(0.8)
                    )
                    .clipShape(Capsule())
                    .overlay(
                        Capsule()
                            .stroke(Color.white.opacity(0.3), lineWidth: 1)
                    )
                    .shadow(color: .black.opacity(0.2), radius: 10, x: 0, y: 5)
            }
            .buttonStyle(ScaleButtonStyle())
            .padding(.bottom, 10)
        }
    }
    
    func getFont(size: CGFloat, weight: Font.Weight) -> Font {
        let design: Font.Design
        switch profile?.fontDesign {
        case "rounded": design = .rounded
        case "serif": design = .serif
        case "monospaced": design = .monospaced
        default: design = .default
        }
        return .system(size: size, weight: weight, design: design)
    }
}

struct CategoriesContentView: View {
    let profile: UserProfile?
    let attributes: [UserAttribute]
    let onBack: () -> Void
    let onSelectCategory: (String) -> Void
    @EnvironmentObject var appManager: AppManager
    
    var categories: [String] {
        // Obtener categorías únicas manteniendo el orden deseado
        let allCategories = attributes.map { $0.category }
        let uniqueCategories = Array(Set(allCategories))
        // Orden específico
        let order = ["Físico", "Mental", "Social", "Otros"]
        return uniqueCategories.sorted { cat1, cat2 in
            let index1 = order.firstIndex(of: cat1) ?? 999
            let index2 = order.firstIndex(of: cat2) ?? 999
            return index1 < index2
        }
    }
    
    var body: some View {
        VStack(spacing: 20) {
            // Cabecera
            HStack {
                Text("Categorías")
                    .font(getFont(size: 28, weight: .bold))
                    .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF"))
                
                Spacer()
                
                Button(action: {
                    appManager.playHaptic()
                    onBack()
                }) {
                    Image(systemName: "xmark.circle.fill")
                    .font(.title2)
                    .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF").opacity(0.8))
                }
                .buttonStyle(ScaleButtonStyle())
            }
            .padding(.bottom, 10)
            
            // Lista de Categorías
            ScrollView(showsIndicators: false) {
                VStack(spacing: 15) {
                    ForEach(categories, id: \.self) { categoryName in
                        Button(action: {
                            appManager.playHaptic()
                            onSelectCategory(categoryName)
                        }) {
                            HStack {
                                Text(categoryName)
                                    .font(getFont(size: 18, weight: .medium))
                                    .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF"))
                                
                                Spacer()
                                
                                Image(systemName: "chevron.right")
                                    .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF").opacity(0.6))
                            }
                            .padding()
                            .background(
                                Color(hex: profile?.buttonBackgroundColorHex ?? "#FFFFFF")
                                    .opacity(0.2)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 16))
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(Color.white.opacity(0.1), lineWidth: 1)
                            )
                        }
                        .buttonStyle(ScaleButtonStyle())
                    }
                }
                .padding(.vertical, 5)
            }
        }
    }
    
    func getFont(size: CGFloat, weight: Font.Weight) -> Font {
        let design: Font.Design
        switch profile?.fontDesign {
        case "rounded": design = .rounded
        case "serif": design = .serif
        case "monospaced": design = .monospaced
        default: design = .default
        }
        return .system(size: size, weight: weight, design: design)
    }
}

struct StatsContentView: View {
    let categoryName: String
    let attributes: [UserAttribute]
    let page: Int
    let profile: UserProfile?
    let onBack: () -> Void
    let onPageChange: (Int) -> Void
    @EnvironmentObject var appManager: AppManager
    
    var body: some View {
        let itemsPerPage = 5
        let totalPages = max(1, Int(ceil(Double(attributes.count) / Double(itemsPerPage))))
        let startIndex = page * itemsPerPage
        let endIndex = min(startIndex + itemsPerPage, attributes.count)
        let pageStats = Array(attributes[startIndex..<endIndex])
        
        return VStack(spacing: 15) {
            // Cabecera
            HStack {
                Button(action: {
                    appManager.playHaptic()
                    onBack()
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "chevron.left")
                        Text("Atrás")
                    }
                    .font(getFont(size: 16, weight: .medium))
                    .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF"))
                }
                .buttonStyle(ScaleButtonStyle())
                
                Spacer()
                
                Text("\(categoryName)")
                    .font(getFont(size: 18, weight: .semibold))
                    .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF"))
                    .lineLimit(1)
            }
            .padding(.bottom, 5)
            
            // Lista de Estadísticas
            ScrollView(showsIndicators: false) {
                VStack(spacing: 12) {
                    ForEach(pageStats) { stat in
                        VStack(alignment: .leading, spacing: 8) {
                            HStack {
                                Text(stat.name)
                                    .font(getFont(size: 16, weight: .bold))
                                    .foregroundColor(Color(hex: "#FFD700"))
                                Spacer()
                                Text(String(format: "%.1f", stat.value))
                                    .font(getFont(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            
                            Text(stat.descriptionText)
                                .font(getFont(size: 14, weight: .regular))
                                .foregroundColor(Color(hex: profile?.buttonTextColorHex ?? "#FFFFFF").opacity(0.9))
                                .fixedSize(horizontal: false, vertical: true)
                            
                            // Barra de Comparación
                            VStack(spacing: 4) {
                                GeometryReader { geometry in
                                    ZStack(alignment: .leading) {
                                        // Fondo de la barra
                                        Capsule()
                                            .fill(Color.white.opacity(0.2))
                                            .frame(height: 8)
                                        
                                        // Barra de valor usuario
                                        Capsule()
                                            .fill(Color(hex: "#4A90E2")) // Azul para usuario
                                            .frame(width: min(geometry.size.width * (stat.value / 10.0), geometry.size.width), height: 8)
                                        
                                        // Marcador de promedio
                                        Rectangle()
                                            .fill(Color.red.opacity(0.8))
                                            .frame(width: 2, height: 12)
                                            .offset(x: min(geometry.size.width * (stat.averageValue / 10.0), geometry.size.width))
                                    }
                                }
                                .frame(height: 12)
                                
                                HStack {
                                    Text("Usuario")
                                        .font(.caption2)
                                        .foregroundColor(Color(hex: "#4A90E2"))
                                    Spacer()
                                    Text("Promedio")
                                        .font(.caption2)
                                        .foregroundColor(.red.opacity(0.8))
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(15)
                        .background(
                            Color(hex: profile?.buttonBackgroundColorHex ?? "#FFFFFF")
                                .opacity(0.15)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                }
                .padding(.vertical, 5)
            }
            
            // Paginación
            if totalPages > 1 {
                HStack {
                    Button(action: {
                        if page > 0 {
                            appManager.playHaptic()
                            onPageChange(page - 1)
                        }
                    }) {
                        Image(systemName: "arrow.left.circle.fill")
                            .font(.title2)
                            .foregroundColor(page > 0 ? Color.white : Color.white.opacity(0.3))
                    }
                    .disabled(page == 0)
                    .buttonStyle(ScaleButtonStyle())
                    
                    Text("\(page + 1) / \(totalPages)")
                        .font(getFont(size: 14, weight: .medium))
                        .foregroundColor(.white.opacity(0.8))
                        .monospacedDigit()
                    
                    Button(action: {
                        if page < totalPages - 1 {
                            appManager.playHaptic()
                            onPageChange(page + 1)
                        }
                    }) {
                        Image(systemName: "arrow.right.circle.fill")
                            .font(.title2)
                            .foregroundColor(page < totalPages - 1 ? Color.white : Color.white.opacity(0.3))
                    }
                    .disabled(page >= totalPages - 1)
                    .buttonStyle(ScaleButtonStyle())
                }
                .padding(.top, 5)
            }
        }
    }
    
    func getFont(size: CGFloat, weight: Font.Weight) -> Font {
        let design: Font.Design
        switch profile?.fontDesign {
        case "rounded": design = .rounded
        case "serif": design = .serif
        case "monospaced": design = .monospaced
        default: design = .default
        }
        return .system(size: size, weight: weight, design: design)
    }
}

// MARK: - Estilo de Botón Personalizado
struct ScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .opacity(configuration.isPressed ? 0.8 : 1.0)
            .animation(.easeInOut(duration: 0.1), value: configuration.isPressed)
            .contentShape(Rectangle()) // CRÍTICO: Asegura que toda el área del botón sea táctil
    }
}

// MARK: - Speech Manager
@Observable
class SpeechManager: NSObject, SFSpeechRecognizerDelegate, AVSpeechSynthesizerDelegate {
    var isRecording = false
    var isSpeaking = false
    var transcribedText = ""
    var errorMessage: String?
    
    private let speechRecognizer = SFSpeechRecognizer(locale: Locale(identifier: "es-ES"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private let audioEngine = AVAudioEngine()
    private let synthesizer = AVSpeechSynthesizer()
    
    override init() {
        super.init()
        speechRecognizer?.delegate = self
        synthesizer.delegate = self
        requestPermissions()
    }
    
    func requestPermissions() {
        SFSpeechRecognizer.requestAuthorization { authStatus in
            DispatchQueue.main.async {
                switch authStatus {
                case .authorized:
                    print("Speech recognition authorized")
                case .denied, .restricted, .notDetermined:
                    self.errorMessage = "Permiso de reconocimiento de voz denegado"
                @unknown default:
                    break
                }
            }
        }
        
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            if !granted {
                DispatchQueue.main.async {
                    self.errorMessage = "Permiso de micrófono denegado"
                }
            }
        }
    }
    
    // MARK: - Speech to Text (STT)
    
    func startRecording() throws {
        if recognitionTask != nil {
            recognitionTask?.cancel()
            recognitionTask = nil
        }
        
        let audioSession = AVAudioSession.sharedInstance()
        try audioSession.setCategory(.record, mode: .measurement, options: .duckOthers)
        try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        
        recognitionRequest = SFSpeechAudioBufferRecognitionRequest()
        
        let inputNode = audioEngine.inputNode
        guard let recognitionRequest = recognitionRequest else {
            fatalError("Unable to create an SFSpeechAudioBufferRecognitionRequest object")
        }
        
        recognitionRequest.shouldReportPartialResults = true
        
        recognitionTask = speechRecognizer?.recognitionTask(with: recognitionRequest) { result, error in
            var isFinal = false
            
            if let result = result {
                self.transcribedText = result.bestTranscription.formattedString
                isFinal = result.isFinal
            }
            
            if error != nil || isFinal {
                self.audioEngine.stop()
                inputNode.removeTap(onBus: 0)
                
                self.recognitionRequest = nil
                self.recognitionTask = nil
                self.isRecording = false
            }
        }
        
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        inputNode.installTap(onBus: 0, bufferSize: 1024, format: recordingFormat) { (buffer, when) in
            self.recognitionRequest?.append(buffer)
        }
        
        audioEngine.prepare()
        try audioEngine.start()
        
        transcribedText = ""
        isRecording = true
    }
    
    func stopRecording() {
        audioEngine.stop()
        recognitionRequest?.endAudio()
        isRecording = false
    }
    
    // MARK: - Text to Speech (TTS)
    
    func getVoices() -> [AVSpeechSynthesisVoice] {
        // Filtrar voces en español
        return AVSpeechSynthesisVoice.speechVoices().filter { $0.language.starts(with: "es") }
    }
    
    func speak(_ text: String, rate: Float = 0.5, pitch: Float = 1.0, volume: Float = 1.0, voiceIdentifier: String = "") {
        // Detener cualquier reproducción anterior
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        
        let utterance = AVSpeechUtterance(string: text)
        
        // Intentar usar la voz seleccionada, si no, usar default es-ES
        if !voiceIdentifier.isEmpty, let selectedVoice = AVSpeechSynthesisVoice(identifier: voiceIdentifier) {
            utterance.voice = selectedVoice
        } else {
            utterance.voice = AVSpeechSynthesisVoice(language: "es-ES")
        }
        
        utterance.rate = rate
        utterance.pitchMultiplier = pitch
        utterance.volume = volume
        
        // Configurar sesión de audio para reproducción
        do {
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .voicePrompt)
            try AVAudioSession.sharedInstance().setActive(true)
        } catch {
            print("Error setting audio session for playback: \(error)")
        }
        
        synthesizer.speak(utterance)
        isSpeaking = true
    }
    
    func stopSpeaking() {
        synthesizer.stopSpeaking(at: .immediate)
        isSpeaking = false
    }
    
    // MARK: - AVSpeechSynthesizerDelegate
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        isSpeaking = false
    }
    
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        isSpeaking = false
    }
}
