//
//  HomeStyleView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/2/24.
//

import SwiftUI
import SwiftData

struct HomeStyleView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appManager: AppManager
    @Bindable var profile: UserProfile
    
    var body: some View {
        Form {
            Section {
                Text("Personaliza la apariencia de tu pantalla de inicio.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
            }
            
            Section(header: Text("Transparencia y Opacidad")) {
                // Transparencia (Luz)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Transparencia (Luz)")
                        .font(.subheadline)
                    
                    HStack {
                        Image(systemName: "sun.min")
                        Slider(value: $profile.containerAlpha, in: 0.0...1.0)
                        Image(systemName: "sun.max")
                    }
                    .tint(LyrionTheme.primaryPurple)
                }
                .padding(.vertical, 4)
                
                // Opacidad (Polarizado)
                VStack(alignment: .leading, spacing: 8) {
                    Text("Opacidad (Polarizado)")
                        .font(.subheadline)
                    
                    HStack {
                        Image(systemName: "circle.dotted")
                        Slider(value: $profile.containerOpacity, in: 0.0...1.0)
                        Image(systemName: "circle.fill")
                    }
                    .tint(LyrionTheme.primaryPurple)
                }
                .padding(.vertical, 4)
            }
            
            Section(header: Text("Colores")) {
                // Color del Contenedor
                VStack(alignment: .leading, spacing: 8) {
                    Text("Color de Fondo")
                        .font(.subheadline)
                    
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            let bgColors = ["#000000", "#FFFFFF", "#1A1A1A", "#2C3E50", "#34495E", "#4A235A", "#1B4F72", "#145A32"]
                            ForEach(bgColors, id: \.self) { colorHex in
                                Circle()
                                    .fill(Color(hex: colorHex))
                                    .frame(width: 30, height: 30)
                                    .overlay(
                                        Circle()
                                            .stroke(Color.gray, lineWidth: profile.containerColorHex == colorHex ? 2 : 0)
                                    )
                                    .onTapGesture {
                                        withAnimation {
                                            profile.containerColorHex = colorHex
                                        }
                                        appManager.playHaptic()
                                    }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                .padding(.vertical, 4)
                
                // Color del Borde
                VStack(alignment: .leading, spacing: 8) {
                    Text("Color del Borde")
                        .font(.subheadline)
                    
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 12) {
                            let borderColors = ["#FFFFFF", "#FF0000", "#00FF00", "#0000FF", "#FFFF00", "#FF00FF", "#00FFFF", "#000000"]
                            ForEach(borderColors, id: \.self) { colorHex in
                                Circle()
                                    .fill(Color(hex: colorHex))
                                    .frame(width: 30, height: 30)
                                    .overlay(
                                        Circle()
                                            .stroke(Color.gray, lineWidth: profile.containerBorderColorHex == colorHex ? 2 : 0)
                                    )
                                    .onTapGesture {
                                        withAnimation {
                                            profile.containerBorderColorHex = colorHex
                                        }
                                        appManager.playHaptic()
                                    }
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                .padding(.vertical, 4)
            }
            
            Section(header: Text("Textos y Botones")) {
                // Color del Título
                ColorPicker("Color del Título", selection: Binding(
                    get: { Color(hex: profile.titleColorHex) },
                    set: { profile.titleColorHex = $0.toHex() ?? "#000000" }
                ))
                
                // Color del Subtítulo
                ColorPicker("Color del Subtítulo", selection: Binding(
                    get: { Color(hex: profile.subtitleColorHex) },
                    set: { profile.subtitleColorHex = $0.toHex() ?? "#000000" }
                ))
                
                // Color del Texto del Botón
                ColorPicker("Texto del Botón", selection: Binding(
                    get: { Color(hex: profile.buttonTextColorHex) },
                    set: { profile.buttonTextColorHex = $0.toHex() ?? "#000000" }
                ))
                
                // Color de Fondo del Botón
                ColorPicker("Fondo del Botón", selection: Binding(
                    get: { Color(hex: profile.buttonBackgroundColorHex) },
                    set: { profile.buttonBackgroundColorHex = $0.toHex() ?? "#FFFFFF" }
                ))
                
                // Diseño de Fuente
                Picker("Diseño de Fuente", selection: $profile.fontDesign) {
                    Text("Redondeada").tag("rounded")
                    Text("Estándar").tag("default")
                    Text("Monoespaciada").tag("monospaced")
                    Text("Serif").tag("serif")
                }
            }
            
            Section {
                Button(action: {
                    appManager.playHaptic()
                    dismiss()
                }) {
                    Text("Guardar Cambios")
                        .frame(maxWidth: .infinity)
                        .foregroundColor(.white)
                        .padding()
                        .background(LyrionTheme.primaryPurple)
                        .cornerRadius(10)
                }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
        }
        .navigationTitle("Personalizar Inicio")
        .navigationBarTitleDisplayMode(.inline)
    }
}
