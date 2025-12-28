//
//  UserAttribute.swift
//  Lyrion
//
//

import Foundation
import SwiftData

@Model
class UserAttribute {
    @Attribute(.unique) var id: UUID
    var category: String // "Físico", "Mental", "Social", "Otros"
    var name: String
    var descriptionText: String
    var value: Double // Valor actual del usuario (0-10)
    var averageValue: Double // Valor promedio para comparación (0-10)
    var order: Int // Para mantener el orden de visualización
    
    init(category: String, name: String, descriptionText: String, value: Double = 0.0, averageValue: Double = 5.0, order: Int = 0) {
        self.id = UUID()
        self.category = category
        self.name = name
        self.descriptionText = descriptionText
        self.value = value
        self.averageValue = averageValue
        self.order = order
    }
}
