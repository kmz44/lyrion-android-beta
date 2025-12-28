//
//  UserDataView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/2/24.
//

import SwiftUI
import SwiftData
import PhotosUI

struct UserDataView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appManager: AppManager
    @EnvironmentObject var authManager: AuthManager
    @Bindable var profile: UserProfile
    
    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImage: Image?
    @State private var isLoadingImage = false
    @Environment(\.modelContext) private var modelContext
    
    @State private var isSaving = false
    @State private var errorMessage: String?
    @State private var showError = false

    var body: some View {
        Form {
            Section {
                VStack(spacing: 20) {
                    if let selectedImage {
                        selectedImage
                            .resizable()
                            .scaledToFill()
                            .frame(width: 100, height: 100)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.white, lineWidth: 2))
                            .shadow(radius: 5)
                    } else if let localData = profile.localAvatarData, let uiImage = UIImage(data: localData) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(width: 100, height: 100)
                            .clipShape(Circle())
                            .overlay(Circle().stroke(Color.white, lineWidth: 2))
                            .shadow(radius: 5)
                    } else if let avatarUrl = profile.avatarUrl, let url = URL(string: avatarUrl) {
                        AsyncImage(url: url) { image in
                            image
                                .resizable()
                                .scaledToFill()
                        } placeholder: {
                            ProgressView()
                        }
                        .frame(width: 100, height: 100)
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.white, lineWidth: 2))
                        .shadow(radius: 5)
                    } else {
                        Image(systemName: "person.circle.fill")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 100, height: 100)
                            .foregroundColor(.gray)
                    }
                    
                    PhotosPicker(selection: $selectedItem, matching: .images) {
                        Text("Cambiar Foto")
                            .font(.headline)
                            .foregroundColor(LyrionTheme.primaryPurple)
                    }
                }
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
                
                Text("Actualiza tu información personal.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
                    .multilineTextAlignment(.center)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 10, leading: 0, bottom: 0, trailing: 0))
            }
            
            Section(header: Text("Información Básica")) {
                HStack {
                    Text("Nombre")
                    Spacer()
                    TextField("Nombre", text: $profile.name)
                        .multilineTextAlignment(.trailing)
                }
                
                HStack {
                    Text("Apellido")
                    Spacer()
                    TextField("Apellido", text: $profile.lastName)
                        .multilineTextAlignment(.trailing)
                }
                
                HStack {
                    Text("Edad")
                    Spacer()
                    TextField("Edad", value: $profile.age, formatter: NumberFormatter())
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                }
                
                HStack {
                    Text("Estado Civil")
                    Spacer()
                    TextField("Estado Civil", text: $profile.maritalStatus)
                        .multilineTextAlignment(.trailing)
                }
            }
            
            Section(header: Text("Físico")) {
                HStack {
                    Text("Altura (cm)")
                    Spacer()
                    TextField("Altura", value: $profile.height, format: .number)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                }
                Toggle("Altura Aproximada", isOn: $profile.isHeightApproximate)
                
                HStack {
                    Text("Peso (kg)")
                    Spacer()
                    TextField("Peso", value: $profile.weight, format: .number)
                        .keyboardType(.decimalPad)
                        .multilineTextAlignment(.trailing)
                }
                Toggle("Peso Aproximado", isOn: $profile.isWeightApproximate)
            }
            
            Section(header: Text("Ubicación")) {
                HStack {
                    Text("País")
                    Spacer()
                    TextField("País", text: $profile.country)
                        .multilineTextAlignment(.trailing)
                }
                
                HStack {
                    Text("Estado/Provincia")
                    Spacer()
                    TextField("Estado", text: $profile.state)
                        .multilineTextAlignment(.trailing)
                }
                }

            
            Section(header: Text("Perfil Profesional")) {
                HStack {
                    Text("Ocupación")
                    Spacer()
                    TextField("Ej. Digital Creator", text: Binding(
                        get: { profile.occupation ?? "" },
                        set: { profile.occupation = $0 }
                    ))
                    .multilineTextAlignment(.trailing)
                }
                
                VStack(alignment: .leading) {
                    Text("Biografía")
                    TextEditor(text: Binding(
                        get: { profile.bio ?? "" },
                        set: { profile.bio = $0 }
                    ))
                    .frame(height: 80)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.2), lineWidth: 1))
                }
            }
            
            Section {
                Button(action: {
                    appManager.playHaptic()
                    saveProfile()
                }) {
                    HStack {
                        if isSaving {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .padding(.trailing, 5)
                            Text("Guardando...")
                        } else {
                            Text("Guardar Cambios")
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .foregroundColor(.white)
                    .padding()
                    .background(isSaving ? Color.gray : LyrionTheme.primaryPurple)
                    .cornerRadius(10)
                }
                .disabled(isSaving)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.clear)
            }
        }
        .navigationTitle("Datos del Usuario")
        .navigationBarTitleDisplayMode(.inline)
        .disabled(isSaving)
        .alert("Error", isPresented: $showError) {
            Button("OK", role: .cancel) { }
        } message: {
            Text(errorMessage ?? "Ha ocurrido un error desconocido.")
        }
        .onChange(of: selectedItem) { oldValue, newItem in
            loadImage()
        }
        .onAppear {
            if let localData = profile.localAvatarData, let uiImage = UIImage(data: localData) {
                selectedImage = Image(uiImage: uiImage)
            }
        }
    }
    
    func loadImage() {
        Task {
            if let data = try? await selectedItem?.loadTransferable(type: Data.self) {
                if let uiImage = UIImage(data: data) {
                    await MainActor.run {
                        selectedImage = Image(uiImage: uiImage)
                        // Convertir siempre a JPEG
                        if let jpegData = uiImage.jpegData(compressionQuality: 0.8) {
                            profile.localAvatarData = jpegData
                        } else {
                            profile.localAvatarData = data
                        }
                    }
                }
            }
        }
    }
    
    func saveProfile() {
        isSaving = true
        
        Task {
            guard let user = authManager.currentUser else {
                await MainActor.run { isSaving = false }
                return
            }
            
            var avatarUrlToSave = profile.avatarUrl
            var uploadError: Error?
            
            // 1. Subir Avatar si hay uno nuevo local
            if let localData = profile.localAvatarData {
                do {
                    print("Subiendo avatar...")
                    let url = try await SupabaseClient.shared.uploadAvatar(userId: user.id, data: localData)
                    avatarUrlToSave = url
                    
                    await MainActor.run {
                        profile.avatarUrl = url
                    }
                    print("Avatar subido: \(url)")
                } catch {
                    print("Error subiendo avatar: \(error)")
                    uploadError = error
                }
            }
            
            // Si falló la subida, mostramos error y paramos
            if let error = uploadError {
                await MainActor.run {
                    errorMessage = "No se pudo subir la imagen: \(error.localizedDescription)"
                    showError = true
                    isSaving = false
                }
                return
            }
            
            // 2. Guardar Perfil en Supabase
            let userUUID = UUID(uuidString: user.id) ?? UUID()
            
            let profileDTO = ProfileDTO(
                id: userUUID,
                username: user.email,
                nombre: profile.name,
                apellido: profile.lastName,
                avatarUrl: avatarUrlToSave,
                bannerUrl: nil,
                bio: profile.bio,
                occupation: profile.occupation,
                country: profile.country,
                edad: profile.age,
                altura: Int(profile.height),
                peso: Int(profile.weight),
                estadoCivil: profile.maritalStatus,
                estadoRegion: profile.state,
                isActive: true,
                lastActiveAt: ISO8601DateFormatter().string(from: Date())
            )
            
            do {
                try await SupabaseClient.shared.updateUserProfile(id: user.id, profile: profileDTO)
                print("Perfil guardado en Supabase")
                
                // 3. Guardar localmente solo si todo salió bien
                try? modelContext.save()
                
                await MainActor.run {
                    isSaving = false
                    dismiss()
                    appManager.playHaptic()
                }
            } catch {
                print("Error guardando perfil en Supabase: \(error)")
                await MainActor.run {
                    errorMessage = "No se pudo guardar el perfil: \(error.localizedDescription)"
                    showError = true
                    isSaving = false
                }
            }
        }
    }
}
