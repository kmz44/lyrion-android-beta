//
//  UserProfile.swift
//  Lyrion
//
//  Created by Lyrion Team on 11/29/24.
//

import Foundation
import SwiftData

@Model
final class UserProfile {
    var name: String
    var lastName: String
    var age: Int
    var height: Double // in cm
    var weight: Double // in kg
    var isHeightApproximate: Bool
    var isWeightApproximate: Bool
    var maritalStatus: String
    var country: String
    var state: String
    
    // Appearance settings
    var titleColorHex: String
    var subtitleColorHex: String
    var buttonTextColorHex: String
    var buttonBackgroundColorHex: String
    var fontDesign: String
    
    // New customization options
    var containerOpacity: Double // Color Tint Opacity
    var containerAlpha: Double   // Material/Blur Visibility
    var containerBorderColorHex: String
    var containerColorHex: String
    
    var createdAt: Date
    
    // Avatar
    var avatarUrl: String?
    @Attribute(.externalStorage) var localAvatarData: Data?
    
    // User Identity
    var userId: String?
    
    // New fields for extended profile
    var occupation: String?
    var bio: String?
    
    init(name: String, lastName: String, age: Int, height: Double, weight: Double, isHeightApproximate: Bool = false, isWeightApproximate: Bool = false, maritalStatus: String, country: String, state: String, titleColorHex: String = "#444444", subtitleColorHex: String = "#444444", buttonTextColorHex: String = "#444444", buttonBackgroundColorHex: String = "#FFFFFF", fontDesign: String = "rounded", containerOpacity: Double = 0.3, containerAlpha: Double = 1.0, containerBorderColorHex: String = "#FFFFFF", containerColorHex: String = "#000000", avatarUrl: String? = nil, localAvatarData: Data? = nil, userId: String? = nil, occupation: String? = nil, bio: String? = nil) {
        self.name = name
        self.lastName = lastName
        self.age = age
        self.height = height
        self.weight = weight
        self.isHeightApproximate = isHeightApproximate
        self.isWeightApproximate = isWeightApproximate
        self.maritalStatus = maritalStatus
        self.country = country
        self.state = state
        self.titleColorHex = titleColorHex
        self.subtitleColorHex = subtitleColorHex
        self.buttonTextColorHex = buttonTextColorHex
        self.buttonBackgroundColorHex = buttonBackgroundColorHex
        self.fontDesign = fontDesign
        self.containerOpacity = containerOpacity
        self.containerAlpha = containerAlpha
        self.containerBorderColorHex = containerBorderColorHex
        self.containerColorHex = containerColorHex
        self.avatarUrl = avatarUrl
        self.localAvatarData = localAvatarData
        self.userId = userId
        self.occupation = occupation
        self.bio = bio
        self.createdAt = Date()
    }
}
