//
//  EditChatView.swift
//  Lyrion
//

import SwiftUI
import SwiftData

struct EditChatView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appManager: AppManager
    @Bindable var thread: Thread
    @State private var chatName: String = ""
    @State private var customSystemPrompt: String = ""
    @State private var showDeleteConfirmation = false
    @Environment(\.modelContext) var modelContext
    
    @ViewBuilder
    private var backgroundColor: some View {
        #if os(iOS)
        Color(UIColor.tertiarySystemBackground)
        #else
        Color.gray.opacity(0.1)
        #endif
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section(header: Text("Información del Chat")) {
                    TextField("Nombre del chat", text: $chatName)
                        .onChange(of: chatName) { oldValue, newValue in
                            // Actualizar el primer mensaje como título
                            if let firstMessage = thread.sortedMessages.first {
                                firstMessage.content = newValue
                            }
                        }
                }
                
                Section(header: Text("System Prompt")) {
                    TextEditor(text: $customSystemPrompt)
                        .frame(minHeight: 120)
                        .onChange(of: customSystemPrompt) { oldValue, newValue in
                            // Guardar el system prompt personalizado
                            thread.customSystemPrompt = newValue
                        }
                }
                .listRowBackground(backgroundColor)
                
                Section {
                    Button(role: .destructive, action: {
                        showDeleteConfirmation = true
                    }) {
                        HStack {
                            Image(systemName: "trash")
                            Text("Eliminar chat")
                        }
                        .frame(maxWidth: .infinity)
                    }
                }
            }
            .navigationTitle("Editar Chat")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                #if os(iOS)
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Listo") {
                        dismiss()
                    }
                    .foregroundColor(LyrionTheme.primaryPurple)
                }
                
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
                #else
                ToolbarItem(placement: .confirmationAction) {
                    Button("Listo") {
                        dismiss()
                    }
                    .foregroundColor(LyrionTheme.primaryPurple)
                }
                
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
                #endif
            }
            .alert("Eliminar Chat", isPresented: $showDeleteConfirmation) {
                Button("Cancelar", role: .cancel) {}
                Button("Eliminar", role: .destructive) {
                    deleteChat()
                }
            } message: {
                Text("¿Estás seguro de que quieres eliminar este chat? Esta acción no se puede deshacer.")
            }
            .onAppear {
                // Cargar datos existentes
                if let firstMessage = thread.sortedMessages.first {
                    chatName = firstMessage.content
                }
                customSystemPrompt = thread.customSystemPrompt ?? appManager.systemPrompt
            }
        }
    }
    
    private func deleteChat() {
        modelContext.delete(thread)
        try? modelContext.save()
        dismiss()
    }
}
