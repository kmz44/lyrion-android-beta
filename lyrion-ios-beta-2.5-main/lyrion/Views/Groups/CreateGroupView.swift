
import SwiftUI

struct CreateGroupView: View {
    @Environment(\.dismiss) var dismiss
    var onGroupCreated: ((SupabaseClient.GroupDTO) -> Void)?
    
    @State private var name = ""
    @State private var description = ""
    @State private var selectedType = "public"
    @State private var isCreating = false
    @State private var errorMessage: String?
    
    let groupTypes = ["public", "friends_only", "followers_only", "private"]
    
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Detalles del Grupo")) {
                    TextField("Nombre del Grupo", text: $name)
                    TextField("Descripción (Opcional)", text: $description)
                }
                
                Section(header: Text("Tipo de Grupo")) {
                    Picker("Tipo", selection: $selectedType) {
                        ForEach(groupTypes, id: \.self) { type in
                            Text(type.replacingOccurrences(of: "_", with: " ").capitalized)
                                .tag(type)
                        }
                    }
                    .pickerStyle(.menu)
                    
                    Text(typeDescription(for: selectedType))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                
                if let error = errorMessage {
                    Section {
                        Text(error)
                            .foregroundColor(.red)
                    }
                }
                
                Section {
                    Button {
                        createGroup()
                    } label: {
                        if isCreating {
                            ProgressView()
                        } else {
                            Text("Crear Grupo")
                                .frame(maxWidth: .infinity)
                                .foregroundColor(.blue)
                        }
                    }
                    .disabled(name.isEmpty || isCreating)
                }
            }
            .navigationTitle("Nuevo Grupo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
            }
        }
    }
    
    func typeDescription(for type: String) -> String {
        switch type {
        case "public": return "Cualquiera puede ver y unirse a este grupo."
        case "friends_only": return "Solo tus amigos pueden ver y unirse."
        case "followers_only": return "Solo tus seguidores pueden ver y unirse."
        case "private": return "Solo por invitación."
        default: return ""
        }
    }
    
    func createGroup() {
        guard !name.isEmpty else { return }
        isCreating = true
        errorMessage = nil
        
        Task {
            do {
                let newGroup = try await SupabaseClient.shared.createGroup(name: name, description: description, type: selectedType, fileURL: nil)
                await MainActor.run {
                    onGroupCreated?(newGroup)
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    errorMessage = "Error al crear grupo: \(error.localizedDescription)"
                    isCreating = false
                }
            }
        }
    }
}
