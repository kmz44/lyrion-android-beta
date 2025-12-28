//
//  LyrionTheme.swift
//  Lyrion
//
//  Created by AI Assistant
//

import SwiftUI

struct LyrionTheme {
    // Purple/Violet colors inspired by the design
    static let primaryPurple = Color(red: 0.584, green: 0.278, blue: 0.988) // #9547FC
    static let lightPurple = Color(red: 0.769, green: 0.612, blue: 0.988) // #C49CFC  
    static let backgroundPurple = Color(red: 0.965, green: 0.941, blue: 1.0) // #F6F0FF
    
    // Accent colors
    static let accentGreen = Color(red: 0.298, green: 0.867, blue: 0.576) // #4CDD93
    static let accentPink = Color(red: 0.988, green: 0.4, blue: 0.647) // #FC66A5
    
    // Neutral colors
    static let darkGray = Color(red: 0.2, green: 0.2, blue: 0.22)
    static let lightGray = Color(red: 0.95, green: 0.95, blue: 0.97)
}

extension View {
    func lyrionStyle() -> some View {
        self
            .tint(LyrionTheme.primaryPurple)
    }
}
