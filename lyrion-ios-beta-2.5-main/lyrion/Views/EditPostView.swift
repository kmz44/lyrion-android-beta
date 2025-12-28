//
//  EditPostView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/26/24.
//

import SwiftUI

struct EditPostView: View {
    let post: PostDTO
    @Environment(\.dismiss) var dismiss
    
    @State private var caption: String
    @State private var title: String
    @State private var category: String
    @State private var isSaving = false
    @State private var errorMessage: String?
    
    var onUpdate: () -> Void
    
    init(post: PostDTO, onUpdate: @escaping () -> Void) {
        self.post = post
        _caption = State(initialValue: post.caption ?? "")
        _title = State(initialValue: post.title ?? "")
        _category = State(initialValue: post.category ?? "")
        self.onUpdate = onUpdate
    }
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Contenido")) {
                    TextField("Título", text: $title)
                    TextField("Categoría", text: $category)
                    TextField("Descripción", text: $caption, axis: .vertical)
                        .lineLimit(3...6)
                }
                
                if let errorMessage = errorMessage {
                    Section {
                        Text(errorMessage)
                            .foregroundColor(.red)
                    }
                }
            }
            .navigationTitle("Editar Publicación")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        saveChanges()
                    }
                    .disabled(isSaving)
                }
            }
        }
    }
    
    private func saveChanges() {
        isSaving = true
        errorMessage = nil
        
        Task {
            do {
                try await SupabaseClient.shared.updatePost(
                    postId: post.id.uuidString,
                    caption: caption,
                    title: title.isEmpty ? nil : title,
                    category: category.isEmpty ? nil : category
                )
                
                await MainActor.run {
                    isSaving = false
                    onUpdate()
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    isSaving = false
                    errorMessage = "Error al guardar: \(error.localizedDescription)"
                }
            }
        }
    }
}
