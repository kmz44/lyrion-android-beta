//
//  AvatarView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/23/24.
//

import SwiftUI

struct AvatarView: View {
    let url: String?
    let name: String?
    let size: CGFloat
    let hasStories: Bool // Nuevo parámetro
    
    // Initializer with default false for backward compatibility
    init(url: String?, name: String?, size: CGFloat, hasStories: Bool = false) {
        self.url = url
        self.name = name
        self.size = size
        self.hasStories = hasStories
    }
    
    var body: some View {
        Group {
            if let urlString = url, let imageUrl = URL(string: urlString) {
                CachedAsyncImage(url: imageUrl) { image in
                    image.resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: size, height: size)
                        .clipShape(Circle())
                } placeholder: {
                    placeholder
                }
            } else {
                placeholder
            }
        }
        .overlay(
            Group {
                if hasStories {
                    Circle()
                        .stroke(
                            LinearGradient(
                                colors: [.purple, .blue, .pink, .orange],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: size * 0.08 // Proportional border width
                        )
                        .padding(-(size * 0.08)) // Expand outwards
                }
            }
        )
    }
    
    var placeholder: some View {
        ZStack {
            Circle()
                .fill(Color(UIColor.secondarySystemBackground))
                .frame(width: size, height: size)
            
            Text(initials)
                .font(.system(size: size * 0.4, weight: .semibold))
                .foregroundColor(.gray)
        }
    }
    
    var initials: String {
        guard let name = name, !name.isEmpty else { return "?" }
        let formatter = PersonNameComponentsFormatter()
        if let components = formatter.personNameComponents(from: name) {
            formatter.style = .abbreviated
            return formatter.string(from: components)
        }
        return String(name.prefix(1)).uppercased()
    }
}
