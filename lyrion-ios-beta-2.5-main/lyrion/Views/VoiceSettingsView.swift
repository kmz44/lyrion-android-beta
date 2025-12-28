//
//  VoiceSettingsView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/3/24.
//

import SwiftUI
import AVFoundation

struct VoiceSettingsView: View {
    @EnvironmentObject var appManager: AppManager
    @Environment(\.dismiss) var dismiss
    
    // Speech synthesizer para probar la voz y obtener voces
    @State private var synthesizer = AVSpeechSynthesizer()
    @State private var availableVoices: [AVSpeechSynthesisVoice] = []
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Voz")) {
                    Picker("Seleccionar Voz", selection: $appManager.ttsVoiceIdentifier) {
                        Text("Predeterminada").tag("")
                        ForEach(availableVoices, id: \.identifier) { voice in
                            Text(voice.name).tag(voice.identifier)
                        }
                    }
                }
                
                Section(header: Text("Velocidad")) {
                    VStack {
                        HStack {
                            Image(systemName: "tortoise.fill")
                            Slider(value: $appManager.ttsRate, in: 0.1...0.9, step: 0.05)
                            Image(systemName: "hare.fill")
                        }
                        Text("\(String(format: "%.2f", appManager.ttsRate))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section(header: Text("Tono")) {
                    VStack {
                        HStack {
                            Text("Grave")
                                .font(.caption)
                            Slider(value: $appManager.ttsPitch, in: 0.5...2.0, step: 0.1)
                            Text("Agudo")
                                .font(.caption)
                        }
                        Text("\(String(format: "%.1f", appManager.ttsPitch))")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section(header: Text("Volumen")) {
                    VStack {
                        HStack {
                            Image(systemName: "speaker.fill")
                            Slider(value: $appManager.ttsVolume, in: 0.1...1.0, step: 0.1)
                            Image(systemName: "speaker.wave.3.fill")
                        }
                        Text("\(Int(appManager.ttsVolume * 100))%")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                
                Section {
                    Button(action: {
                        probarVoz()
                    }) {
                        HStack {
                            Image(systemName: "play.circle.fill")
                            Text("Probar Configuración")
                        }
                        .frame(maxWidth: .infinity)
                        .padding(8)
                    }
                }
                
                Section(footer: Text("Ajusta cómo suena la IA. Puedes elegir entre las voces instaladas en tu dispositivo.")) {
                    Button("Restablecer Valores") {
                        appManager.ttsRate = 0.5
                        appManager.ttsPitch = 1.0
                        appManager.ttsVolume = 1.0
                        appManager.ttsVoiceIdentifier = ""
                    }
                    .foregroundColor(.red)
                }
            }
            .navigationTitle("Ajustes de Voz")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Listo") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                cargarVoces()
            }
        }
    }
    
    func cargarVoces() {
        // Filtrar voces en español
        availableVoices = AVSpeechSynthesisVoice.speechVoices().filter { $0.language.starts(with: "es") }
    }
    
    func probarVoz() {
        if synthesizer.isSpeaking {
            synthesizer.stopSpeaking(at: .immediate)
        }
        
        let utterance = AVSpeechUtterance(string: "Hola, así es como sueno con la configuración actual.")
        
        if !appManager.ttsVoiceIdentifier.isEmpty, let selectedVoice = AVSpeechSynthesisVoice(identifier: appManager.ttsVoiceIdentifier) {
            utterance.voice = selectedVoice
        } else {
            utterance.voice = AVSpeechSynthesisVoice(language: "es-ES")
        }
        
        utterance.rate = Float(appManager.ttsRate)
        utterance.pitchMultiplier = Float(appManager.ttsPitch)
        utterance.volume = Float(appManager.ttsVolume)
        
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .voicePrompt)
        try? AVAudioSession.sharedInstance().setActive(true)
        
        synthesizer.speak(utterance)
    }
}
