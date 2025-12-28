//
//  SocialComponents.swift
//  Lyrion
//
//  Created for Lyrion IA
//

import SwiftUI

struct SuggestedUserCard: View {
    let user: ProfileDTO
    
    var body: some View {
        VStack {
            AvatarView(url: user.avatarUrl, name: user.username, size: 60)
            
            Text(user.username ?? "Usuario")
                .font(.subheadline)
                .fontWeight(.medium)
                .lineLimit(1)
            
            if let name = user.nombre {
                Text(name)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            
            Button("Seguir") {
                Task {
                    do {
                        try await SupabaseClient.shared.followUser(targetId: user.id)
                        print("✅ [UI] Seguido exitosamente desde SuggestedUserCard")
                    } catch {
                        print("❌ [UI] Error en SuggestedUserCard.followUser: \(error)")
                    }
                }
            }
            .font(.caption)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(LyrionTheme.primaryPurple)
            .foregroundColor(.white)
            .cornerRadius(15)
        }
        .padding()
        .frame(width: 140)
        .background(Color(UIColor.systemBackground))
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.1), radius: 3, x: 0, y: 2)
    }
}

struct SearchUserRow: View {
    let user: ProfileDTO
    
    var body: some View {
        HStack(spacing: 15) {
            AvatarView(url: user.avatarUrl, name: user.username, size: 50)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(user.username ?? "Usuario")
                    .font(.headline)
                    .foregroundColor(.primary)
                
                if let bio = user.bio, !bio.isEmpty {
                     Text(bio)
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                } else if let nombre = user.nombre {
                    Text(nombre)
                       .font(.subheadline)
                       .foregroundColor(.secondary)
                       .lineLimit(1)
                }
            }
            Spacer()
            
            Image(systemName: "chevron.right")
                .foregroundColor(.gray)
                .font(.caption)
        }
    }
}
