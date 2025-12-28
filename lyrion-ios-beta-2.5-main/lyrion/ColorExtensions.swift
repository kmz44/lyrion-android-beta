//
//  ColorExtensions.swift
//  Lyrion
//

import SwiftUI

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        
        // Validar que el hex no esté vacío y se pueda parsear
        guard !hex.isEmpty, Scanner(string: hex).scanHexInt64(&int) else {
            // Fallback a negro si el hex es inválido
            self.init(.sRGB, red: 0, green: 0, blue: 0, opacity: 1)
            return
        }
        
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            // Fallback a negro para formatos inválidos
            self.init(.sRGB, red: 0, green: 0, blue: 0, opacity: 1)
            return
        }

        self.init(
            .sRGB,
            red: Double(r) / 255.0,
            green: Double(g) / 255.0,
            blue:  Double(b) / 255.0,
            opacity: Double(a) / 255.0
        )
    }
    
    // Convertir Color a Hex String
    func toHex() -> String? {
        #if os(macOS)
        guard let components = NSColor(self).cgColor.components, components.count >= 3 else {
            return nil
        }
        #else
        guard let components = UIColor(self).cgColor.components, components.count >= 3 else {
            return nil
        }
        #endif
        
        let r = Float(components[0])
        let g = Float(components[1])
        let b = Float(components[2])
        
        return String(format: "#%02lX%02lX%02lX", lroundf(r * 255), lroundf(g * 255), lroundf(b * 255))
    }
}
