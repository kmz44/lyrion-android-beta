//
//  EditProfileView.swift
//  Lyrion
//
//  Created by Lyrion Team on 11/29/24.
//

import SwiftUI
import SwiftData

struct EditProfileView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Bindable var profile: UserProfile
    
    @State private var name = ""
    @State private var lastName = ""
    @State private var ageString = ""
    @State private var heightString = ""
    @State private var weightString = ""
    @State private var isHeightApproximate = false
    @State private var isWeightApproximate = false
    @State private var maritalStatus = ""
    @State private var country = ""
    @State private var state = ""
    
    @State private var titleColor = Color(hex: "#444444")
    @State private var subtitleColor = Color(hex: "#444444")
    @State private var buttonTextColor = Color(hex: "#444444")
    @State private var buttonBackgroundColor = Color(hex: "#FFFFFF")
    @State private var fontDesign = "rounded"
    
    let maritalStatuses = ["Soltero/a", "Casado/a", "Divorciado/a", "Viudo/a", "Unión Libre"]
    let fontDesigns = ["rounded", "serif", "monospaced", "default"]
    
    var isFormValid: Bool {
        !name.isEmpty && !heightString.isEmpty && !weightString.isEmpty && !ageString.isEmpty
    }
    
    var body: some View {
        Form {
            Section(header: Text("Información Personal")) {
                TextField("Nombre", text: $name)
                TextField("Apellidos", text: $lastName)
                TextField("Edad", text: $ageString)
                    .keyboardType(.numberPad)
            }
            
            Section(header: Text("Físico"), footer: Text("Si no conoces los datos exactos, marca la casilla 'Aproximado' para ajustar la precisión.")) {
                HStack {
                    TextField("Estatura (cm)", text: $heightString)
                        .keyboardType(.decimalPad)
                    Toggle("Aproximado", isOn: $isHeightApproximate)
                        .labelsHidden()
                }
                
                HStack {
                    TextField("Peso (kg)", text: $weightString)
                        .keyboardType(.decimalPad)
                    Toggle("Aproximado", isOn: $isWeightApproximate)
                        .labelsHidden()
                }
            }
            
            Section(header: Text("Detalles")) {
                Picker("Estado Civil", selection: $maritalStatus) {
                    ForEach(maritalStatuses, id: \.self) { status in
                        Text(status).tag(status)
                    }
                }
                
                TextField("País", text: $country)
                TextField("Estado/Provincia", text: $state)
            }
            
            Section(header: Text("Personalización de Inicio")) {
                ColorPicker("Color del Título", selection: $titleColor)
                ColorPicker("Color del Subtítulo", selection: $subtitleColor)
                ColorPicker("Color Texto Botón", selection: $buttonTextColor)
                ColorPicker("Color Fondo Botón", selection: $buttonBackgroundColor)
                
                Picker("Estilo de Letra", selection: $fontDesign) {
                    Text("Redondeada").tag("rounded")
                    Text("Serif").tag("serif")
                    Text("Monoespaciada").tag("monospaced")
                    Text("Por Defecto").tag("default")
                }
            }
        }
        .navigationTitle("Editar Perfil")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Guardar") {
                    saveProfile()
                }
                .disabled(!isFormValid)
            }
            
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancelar") {
                    dismiss()
                }
            }
        }
        .onAppear {
            loadData()
        }
    }
    
    private func loadData() {
        name = profile.name
        lastName = profile.lastName
        ageString = String(profile.age)
        heightString = String(profile.height)
        weightString = String(profile.weight)
        isHeightApproximate = profile.isHeightApproximate
        isWeightApproximate = profile.isWeightApproximate
        maritalStatus = profile.maritalStatus
        country = profile.country
        state = profile.state
        
        // Load colors
        titleColor = Color(hex: profile.titleColorHex)
        subtitleColor = Color(hex: profile.subtitleColorHex)
        buttonTextColor = Color(hex: profile.buttonTextColorHex)
        buttonBackgroundColor = Color(hex: profile.buttonBackgroundColorHex)
        fontDesign = profile.fontDesign
    }
    
    private func saveProfile() {
        guard let height = Double(heightString), 
              let weight = Double(weightString),
              let age = Int(ageString) else { return }
        
        profile.name = name
        profile.lastName = lastName
        profile.age = age
        profile.height = height
        profile.weight = weight
        profile.isHeightApproximate = isHeightApproximate
        profile.isWeightApproximate = isWeightApproximate
        profile.maritalStatus = maritalStatus
        profile.country = country
        profile.state = state
        
        // Guardar colores personalizados
        profile.titleColorHex = titleColor.toHex() ?? "#444444"
        profile.subtitleColorHex = subtitleColor.toHex() ?? "#444444"
        profile.buttonTextColorHex = buttonTextColor.toHex() ?? "#444444"
        profile.buttonBackgroundColorHex = buttonBackgroundColor.toHex() ?? "#FFFFFF"
        profile.fontDesign = fontDesign
        
        try? modelContext.save()
        dismiss()
    }
}
