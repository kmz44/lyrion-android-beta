//
//  UserAttributesView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/2/24.
//

import SwiftUI
import SwiftData

struct UserAttributesView: View {
    @Environment(\.dismiss) var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \UserAttribute.order) private var userAttributes: [UserAttribute]
    @EnvironmentObject var appManager: AppManager
    
    // Agrupamos atributos por categoría para mejor organización
    var groupedAttributes: [String: [UserAttribute]] {
        Dictionary(grouping: userAttributes, by: { $0.category })
    }
    
    // Orden deseado de categorías
    let categoryOrder = ["Físico", "Mental", "Social", "Otros"]
    
    var body: some View {
        Form {
            Section {
                Text("Ajusta tus atributos personales. Estos valores definen las capacidades de tu perfil.")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 0, bottom: 0, trailing: 0))
            }
            
            ForEach(categoryOrder, id: \.self) { category in
                if let attributes = groupedAttributes[category], !attributes.isEmpty {
                    Section(header: Text(category)) {
                        ForEach(attributes) { attribute in
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Text(attribute.name)
                                        .font(.subheadline)
                                        .fontWeight(.medium)
                                    Spacer()
                                    Text(String(format: "%.1f", attribute.value))
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                
                                Slider(value: Bindable(attribute).value, in: 0.0...10.0, step: 0.1)
                                    .tint(LyrionTheme.primaryPurple)
                                    .onChange(of: attribute.value) { oldValue, newValue in
                                        // SwiftData guarda automáticamente, pero podemos añadir lógica extra si es necesario
                                    }
                            }
                            .padding(.vertical, 4)
                        }
                    }
                }
            }
            
            Section {
                Button(action: {
                    // SwiftData persiste automáticamente, pero el botón da feedback al usuario
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
        .navigationTitle("Atributos")
        .navigationBarTitleDisplayMode(.inline)
    }
}
