//
//  CreateEventSheet.swift
//  Lyrion
//

import SwiftUI

struct CreateEventSheet: View {
    @Environment(\.dismiss) var dismiss
    @Binding var isPresented: Bool
    var onEventCreated: () async -> Void
    
    @State private var title: String = ""
    @State private var startDate: Date = Date()
    @State private var endDate: Date = Date().addingTimeInterval(3600)
    @State private var description: String = ""
    @State private var isAllDay: Bool = false
    @State private var isCreating: Bool = false
    @State private var showError: Bool = false
    @State private var errorMessage: String = ""
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Detalles del Evento")) {
                    TextField("Título", text: $title)
                    
                    Toggle("Todo el día", isOn: $isAllDay)
                    
                    DatePicker("Inicio", selection: $startDate, displayedComponents: isAllDay ? [.date] : [.date, .hourAndMinute])
                    
                    DatePicker("Fin", selection: $endDate, displayedComponents: isAllDay ? [.date] : [.date, .hourAndMinute])
                }
                
                Section(header: Text("Descripción (Opcional)")) {
                    TextEditor(text: $description)
                        .frame(height: 100)
                }
            }
            .navigationTitle("Nuevo Evento")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") {
                        dismiss()
                    }
                    .disabled(isCreating)
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Crear") {
                        Task {
                            await createEvent()
                        }
                    }
                    .disabled(title.isEmpty || isCreating)
                }
            }
            #else
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        dismiss()
                    }
                    .disabled(isCreating)
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button("Crear") {
                        Task {
                            await createEvent()
                        }
                    }
                    .disabled(title.isEmpty || isCreating)
                }
            }
            #endif
            .alert("Error", isPresented: $showError) {
                Button("OK", role: .cancel) { }
            } message: {
                Text(errorMessage)
            }
            .overlay {
                if isCreating {
                    ZStack {
                        Color.black.opacity(0.3)
                            .ignoresSafeArea()
                        
                        ProgressView()
                            .progressViewStyle(.circular)
                            .scaleEffect(1.5)
                            .tint(.white)
                    }
                }
            }
        }
    }
    
    private func createEvent() async {
        guard let accessToken = GoogleCalendarService.shared.hasValidToken() else {
            errorMessage = "No hay sesión activa"
            showError = true
            return
        }
        
        isCreating = true
        
        do {
            try await GoogleCalendarService.shared.createEvent(
                accessToken: accessToken,
                title: title,
                startDate: startDate,
                endDate: endDate,
                description: description.isEmpty ? nil : description,
                isAllDay: isAllDay
            )
            
            await onEventCreated()
            dismiss()
        } catch let error as GoogleCalendarError {
            errorMessage = error.localizedDescription
            showError = true
        } catch {
            errorMessage = "Error al crear el evento"
            showError = true
        }
        
        isCreating = false
    }
}
