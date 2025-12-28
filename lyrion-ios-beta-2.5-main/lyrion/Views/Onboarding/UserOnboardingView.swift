//
//  UserOnboardingView.swift
//  Lyrion
//
//  Created by Lyrion Team on 11/29/24.
//

import SwiftUI
import SwiftData

struct UserOnboardingView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Binding var showOnboarding: Bool
    
    @State private var name = ""
    @State private var lastName = ""
    @State private var ageString = ""
    @State private var heightString = ""
    @State private var weightString = ""
    @State private var isHeightApproximate = false
    @State private var isWeightApproximate = false
    @State private var maritalStatus = "Soltero/a"
    @State private var country = ""
    @State private var state = ""
    
    let maritalStatuses = ["Soltero/a", "Casado/a", "Divorciado/a", "Viudo/a", "Unión Libre"]
    
    @State private var showAlert = false
    
    var isFormValid: Bool {
        // Only require name, height and weight as critical fields
        !name.isEmpty && !heightString.isEmpty && !weightString.isEmpty && !ageString.isEmpty
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Información Personal")) {
                    TextField("Nombre", text: $name)
                    TextField("Apellidos", text: $lastName)
                    TextField("Edad", text: $ageString)
                        #if os(iOS)
                        .keyboardType(.numberPad)
                        #endif
                }
                
                Section(header: Text("Físico"), footer: Text("Si no conoces los datos exactos, marca la casilla 'Aproximado' para ajustar la precisión.")) {
                    HStack {
                        TextField("Estatura (cm)", text: $heightString)
                            #if os(iOS)
                            .keyboardType(.decimalPad)
                            #endif
                        Toggle("Aproximado", isOn: $isHeightApproximate)
                            .labelsHidden()
                    }
                    
                    HStack {
                        TextField("Peso (kg)", text: $weightString)
                            #if os(iOS)
                            .keyboardType(.decimalPad)
                            #endif
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
                
                Section {
                    Button(action: {
                        if isFormValid {
                            saveProfile()
                        } else {
                            showAlert = true
                        }
                    }) {
                        Text("Comenzar")
                            .frame(maxWidth: .infinity)
                            .bold()
                    }
                }
            }
            .navigationTitle("Bienvenido")
            .navigationBarTitleDisplayMode(.large)
            .alert("Faltan datos", isPresented: $showAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text("Por favor ingresa al menos tu nombre, edad, estatura y peso para continuar.")
            }
        }
    }
    
    private func saveProfile() {
        guard let height = Double(heightString), let weight = Double(weightString), let age = Int(ageString) else { return }
        
        // Logic for approximate values: subtract a small amount (2 units) to reduce precision errors as requested
        let finalHeight = isHeightApproximate ? height - 2.0 : height
        let finalWeight = isWeightApproximate ? weight - 2.0 : weight
        
        // Guardar ID del usuario actual
        let currentUserId = SupabaseClient.shared.loadSession()?.user.id
        
        // Buscar si ya existe un perfil local para este usuario (el placeholder creado en ContentView)
        var targetProfile: UserProfile?
        do {
            let descriptor = FetchDescriptor<UserProfile>()
            let profiles = try modelContext.fetch(descriptor)
            targetProfile = profiles.first { $0.userId == currentUserId }
        } catch {
            print("Error buscando perfil existente: \(error)")
        }
        
        if let existingProfile = targetProfile {
            // Actualizar existente
            print("📝 Actualizando perfil local existente...")
            existingProfile.name = name
            existingProfile.lastName = lastName
            existingProfile.age = age
            existingProfile.height = finalHeight
            existingProfile.weight = finalWeight
            existingProfile.isHeightApproximate = isHeightApproximate
            existingProfile.isWeightApproximate = isWeightApproximate
            existingProfile.maritalStatus = maritalStatus
            existingProfile.country = country
            existingProfile.state = state
            // userId ya debería estar correcto, pero por si acaso
            if existingProfile.userId == nil { existingProfile.userId = currentUserId }
        } else {
            // Crear nuevo si no existe
            print("🆕 Creando nuevo perfil local...")
            let newProfile = UserProfile(
                name: name,
                lastName: lastName,
                age: age,
                height: finalHeight,
                weight: finalWeight,
                isHeightApproximate: isHeightApproximate,
                isWeightApproximate: isWeightApproximate,
                maritalStatus: maritalStatus,
                country: country,
                state: state,
                userId: currentUserId
            )
            modelContext.insert(newProfile)
        }
        
        try? modelContext.save()
        
        // Sincronizar INMEDIATA con Supabase
        if let user = SupabaseClient.shared.loadSession()?.user {
             Task {
                 print("🚀 Subiendo datos de onboarding a la nube...")
                 let userUUID = UUID(uuidString: user.id) ?? UUID()
                 let profileDTO = ProfileDTO(
                     id: userUUID,
                     username: user.email,
                     nombre: name,
                     apellido: lastName,
                     avatarUrl: nil, // Mantenemos el que tenga o nil
                     bannerUrl: nil,
                     bio: nil,
                     occupation: nil,
                     country: country,
                     edad: age,
                     altura: Int(finalHeight),
                     peso: Int(finalWeight),
                     estadoCivil: maritalStatus,
                     estadoRegion: state,
                     isActive: true,
                     lastActiveAt: ISO8601DateFormatter().string(from: Date())
                 )
                 
                 do {
                     try await SupabaseClient.shared.updateUserProfile(id: user.id, profile: profileDTO)
                     print("✅ Onboarding sincronizado correctamente.")
                 } catch {
                     print("❌ Error subiendo onboarding: \(error)")
                 }
                 
                 await MainActor.run {
                     showOnboarding = false
                 }
             }
        } else {
             showOnboarding = false
        }
    }
}

#Preview {
    UserOnboardingView(showOnboarding: .constant(true))
}
