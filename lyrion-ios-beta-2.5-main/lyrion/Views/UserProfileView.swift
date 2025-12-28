//
//  UserProfileView.swift
//  Lyrion
//
//  Created for Lyrion IA
//

import SwiftUI

struct UserProfileView: View {
    let user: ProfileDTO
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var appManager: AppManager
    @State private var relationshipStatus: String = "none"
    @State private var isFollowing: Bool = false
    @State private var friendshipStatus: String = "none"
    @State private var postsCount: Int = 0
    @State private var followersCount: Int = 0
    @State private var followingCount: Int = 0
    @State private var friendsCount: Int = 0
    @State private var userPosts: [PostDTO] = []
    @State private var isLoading: Bool = true

    @State private var selectedTab: ProfileTab = .posts
    @State private var followersList: [ProfileDTO] = [] // Stats: Lista real de seguidores
    
    // Stories
    @State private var hasActiveStories: Bool = false
    @State private var userStories: [SupabaseClient.StoryDTO] = []
    @State private var showStories: Bool = false
    
    enum ProfileTab {
        case posts, videos
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // 1. Custom Header Sticky
            HStack {
                Button(action: { dismiss() }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 22)) // Un poco más grande como el HTML
                        .foregroundColor(.primary)
                        .padding(8)
                        .background(Color.gray.opacity(0.1)) // Círculo hover effect simulación
                        .clipShape(Circle())
                }
                
                Spacer()
                
                Text(user.username ?? "Usuario")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.primary)
                    .lineLimit(1)
                
                Spacer()
                
                Button(action: { /* More options */ }) {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 22))
                        .foregroundColor(.primary)
                        .rotationEffect(.degrees(90)) // Horizontal ellipsis
                        .padding(8)
                        .background(Color.gray.opacity(0.1))
                        .clipShape(Circle())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(Color(UIColor.systemBackground))
            .zIndex(20) // Highest priority
            
            // 2. Main Scroll Content
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    
                    // Banner (Inside Scroll)
                    if let bannerUrl = user.bannerUrl, let url = URL(string: bannerUrl) {
                        AsyncImage(url: url) { phase in
                            switch phase {
                            case .success(let image):
                                image.resizable()
                                     .aspectRatio(contentMode: .fill)
                                     .frame(height: 140) // h-32 aprox + un poco más para estética
                                     .clipped()
                            default:
                                Color.gray.opacity(0.2).frame(height: 140)
                            }
                        }
                        .frame(height: 140)
                    } else {
                        Rectangle()
                            .fill(LinearGradient(colors: [.blue.opacity(0.3), .purple.opacity(0.3)], startPoint: .leading, endPoint: .trailing))
                            .frame(height: 140)
                    }
                    
                    // Profile Info Container
                    VStack(alignment: .leading, spacing: 0) {
                        
                        // Avatar Row with Stats
                        HStack(alignment: .bottom, spacing: 0) {
                            // Avatar
                            ZStack {
                                Circle() // Borde externo del background para recorte limpio
                                    .fill(Color(UIColor.systemBackground))
                                    .frame(width: 104, height: 104)
                                
                                // Story Ring (Gradient) - Only if has active stories
                                if hasActiveStories {
                                    Circle()
                                        .strokeBorder(
                                            LinearGradient(
                                                colors: [.orange, .pink, .purple],
                                                startPoint: .bottomLeading,
                                                endPoint: .topTrailing
                                            ),
                                            lineWidth: 2.5
                                        )
                                        .frame(width: 96, height: 96)
                                }
                                
                                // Avatar Image
                                Button {
                                    if hasActiveStories {
                                        showStories = true
                                    }
                                } label: {
                                    AvatarView(url: user.avatarUrl, name: user.username, size: 88, hasStories: false) // Ring handled externally here
                                        .clipShape(Circle())
                                        .overlay(Circle().stroke(Color(UIColor.systemBackground), lineWidth: 3))
                                }
                                .buttonStyle(PlainButtonStyle())
                                .disabled(!hasActiveStories)
                                
                                // Online Indicator (Green Dot) - Solo mostrar si usuario está activo
                                if user.isActive == true {
                                    VStack {
                                        Spacer()
                                        HStack {
                                            Spacer()
                                            Circle()
                                                .fill(Color.green)
                                                .frame(width: 20, height: 20)
                                                .overlay(Circle().stroke(Color(UIColor.systemBackground), lineWidth: 3))
                                        }
                                    }
                                    .frame(width: 96, height: 96)
                                }
                            }
                            .offset(y: -40) // Negative margin to overlap banner
                            .padding(.bottom, -30) // Reduce impact on flow
                            
                            Spacer(minLength: 16)
                            
                            // Stats (Ahora alineados con el HTML, a la derecha)
                            HStack(spacing: 24) {
                                StatView(count: postsCount, label: "Posts")
                                StatView(count: followersCount, label: "Seguidores")
                                StatView(count: followingCount, label: "Seguidos")
                                StatView(count: friendsCount, label: "Amigos")
                            }
                            .padding(.bottom, 12)
                            .padding(.trailing, 8)
                        }
                        .padding(.horizontal, 4) // Ajuste fino
                        
                        // Bio Text Section
                        VStack(alignment: .leading, spacing: 4) {
                            let displayName = (user.nombre != nil || user.apellido != nil) ? 
                                "\(user.nombre ?? "") \(user.apellido ?? "")".trimmingCharacters(in: .whitespaces) : 
                                (user.username ?? "Usuario")
                            
                            Text(displayName.isEmpty ? (user.username ?? "Usuario") : displayName)
                                .font(.system(size: 18, weight: .bold))
                                .foregroundColor(.primary)
                            
                            // Occupation
                            if let occupation = user.occupation, !occupation.isEmpty {
                                Text(occupation)
                                    .font(.system(size: 15, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                if let bio = user.bio, !bio.isEmpty {
                                    Text(bio).font(.subheadline)
                                } else {
                                    // Placeholder styled like HTML if needed, or empty
                                    Text("🎨 Digital Creator & Designer").font(.subheadline)
                                }
                                // Location placeholder or real data if available
                                if let pais = user.country {
                                    Text("📍 \(user.estadoRegion ?? ""), \(pais)").font(.subheadline)
                                }
                            }
                            .foregroundColor(.primary)
                            
                            // Link
                            HStack(spacing: 4) {
                                Image(systemName: "link")
                                    .font(.caption)
                                    .rotationEffect(.degrees(45))
                                Text("kevinmarquez.design")
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                            }
                            .foregroundColor(.blue)
                            .padding(.top, 2)
                        }
                        .padding(.top, 4)
                        
                        // "Seguido por..." Section
                        followedBySection.padding(.vertical, 16)
                        
                        // Action Buttons Grid
                        HStack(spacing: 12) {
                            Button(action: toggleFollow) {
                                Text(isFollowing ? "Siguiendo" : "Seguir")
                                    .font(.system(size: 14, weight: .semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(isFollowing ? Color(UIColor.secondarySystemBackground) : Color.primary)
                                    .foregroundColor(isFollowing ? .primary : Color(UIColor.systemBackground))
                                    .cornerRadius(8)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color.gray.opacity(0.3), lineWidth: isFollowing ? 1 : 0)
                                    )
                            }
                            
                            NavigationLink(destination: DirectChatView(targetUser: user)) {
                                Text("Mensaje")
                                    .font(.system(size: 14, weight: .semibold))
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(Color(UIColor.secondarySystemBackground))
                                    .foregroundColor(.primary)
                                    .cornerRadius(8)
                                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.3), lineWidth: 1))
                            }
                            
                            if friendshipStatus == "amigos" {
                                Menu {
                                    Button(role: .destructive, action: {
                                        Task {
                                            try? await SupabaseClient.shared.removeFriend(targetId: user.id)
                                            await MainActor.run { friendshipStatus = "none" }
                                        }
                                    }) {
                                        Label("Eliminar amigo", systemImage: "person.badge.minus")
                                    }
                                } label: {
                                    Image(systemName: "person.2.fill")
                                        .font(.system(size: 16))
                                        .frame(width: 44, height: 40)
                                        .background(Color.green.opacity(0.1))
                                        .foregroundColor(.green)
                                        .cornerRadius(8)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.green.opacity(0.3), lineWidth: 1))
                                }
                            } else if friendshipStatus == "pendiente" {
                                Button(action: {}) {
                                    Image(systemName: "hourglass")
                                        .font(.system(size: 16))
                                        .frame(width: 44, height: 40)
                                        .background(Color(UIColor.secondarySystemBackground))
                                        .foregroundColor(.secondary)
                                        .cornerRadius(8)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.3), lineWidth: 1))
                                }
                                .disabled(true)
                            } else {
                                Button(action: sendFriendRequest) {
                                    Image(systemName: "person.badge.plus")
                                        .font(.system(size: 16))
                                        .frame(width: 44, height: 40)
                                        .background(Color(UIColor.secondarySystemBackground))
                                        .foregroundColor(.primary)
                                        .cornerRadius(8)
                                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.gray.opacity(0.3), lineWidth: 1))
                                }
                            }
                        }
                        .padding(.bottom, 24)
                        
                    } // End Profile Info Container
                    .padding(.horizontal, 16)
                    
                    // Sticky Tabs + Grid
                    LazyVStack(spacing: 0, pinnedViews: [.sectionHeaders]) {
                        Section(header: 
                            tabsSection.background(Color(UIColor.systemBackground))
                        ) {
                            switch selectedTab {
                            case .posts:
                                contentGridSection
                            case .videos:
                                // "En videos dejalo como esta ahorita sin nada"
                                Color.clear.frame(height: 200) // Espacio vacío o placeholder
                            }
                        }
                    }
                    
                } // End Main VStack
            } // End ScrollView
        }
        .navigationBarHidden(true) // Hide native bar to use our custom one
        .fullScreenCover(isPresented: $showStories) {
            StoryViewer(stories: userStories)
        }
        .task {
            await loadProfileData()
        }
    }
    
    // MARK: - Helper Views
    
    private var followedBySection: some View {
        Group {
            if !followersList.isEmpty {
                HStack(spacing: 8) {
                    // Mutual friends avatars (Real Data)
                    HStack(spacing: -8) {
                        ForEach(followersList.prefix(3)) { follower in
                            AvatarView(url: follower.avatarUrl, name: follower.username, size: 24)
                                .clipShape(Circle())
                                .overlay(Circle().stroke(Color(UIColor.systemBackground), lineWidth: 2))
                        }
                    }
                    
                    HStack(spacing: 0) {
                        Text("Seguido por ")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        
                        if let first = followersList.first {
                            Text(first.username ?? "Usuario")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.primary)
                            
                            if followersList.count > 1 {
                                Text(" y \(followersList.count - 1) más")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                }
            } else {
                EmptyView()
            }
        }
    }


    
    private var tabsSection: some View {
        HStack(spacing: 0) {
            TabButton(icon: "square.grid.3x3", isSelected: selectedTab == .posts) {
                selectedTab = .posts
            }
            TabButton(icon: "play.rectangle", isSelected: selectedTab == .videos) {
                selectedTab = .videos
            }
        }
        .frame(height: 48)
        .background(Color(UIColor.systemBackground))
        .overlay(Divider(), alignment: .top)
        .overlay(Divider(), alignment: .bottom)
    }
    
    private var contentGridSection: some View {
        VStack(spacing: 0) {
            if isLoading {
                ProgressView("Cargando posts...")
                    .padding()
            } else if userPosts.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "photo.on.rectangle.angled")
                        .font(.system(size: 48))
                        .foregroundColor(.gray)
                    
                    Text("No hay publicaciones aún")
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text("Cuando \(user.username ?? "este usuario") publique algo, aparecerá aquí")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
                .padding(.vertical, 60)
            } else {
                // Mostrar posts en formato scroll vertical (como en el feed)
                ForEach(userPosts) { post in
                    SocialPostView(post: post)
                        .padding(.bottom, 12)
                }
            }
        }
    }
    
    private var placeholderItem: some View {
        Rectangle()
            .fill(Color.gray.opacity(0.2))
            .aspectRatio(1, contentMode: .fill)
    }
    
    // MARK: - Helper Functions
    private func loadProfileData() async {
        isLoading = true
        
        // Load relationship status
        do {
            let status = try await SupabaseClient.shared.checkRelationshipStatus(with: user.id)
            await MainActor.run {
                self.relationshipStatus = status
                // Parse is_following and friendship_status from combined status if needed
                self.isFollowing = status.contains("siguiendo") || status == "amigos"
                self.friendshipStatus = status == "amigos" ? "amigos" : (status.contains("pendiente") ? "pendiente" : "none")
            }
        } catch {
            print("Error loading relationship status: \(error)")
        }
        
        // Load stats
        do {
            let stats = try await SupabaseClient.shared.countUserStats(userId: user.id)
            await MainActor.run {
                self.postsCount = stats.posts
                self.followersCount = stats.followers
                self.followingCount = stats.following
                self.friendsCount = stats.friends
            }
        } catch {
            print("Error loading stats: \(error)")
        }
        
        // Load posts
        do {
            let posts = try await SupabaseClient.shared.fetchUserPosts(userId: user.id)
            await MainActor.run {
                self.userPosts = posts
                self.isLoading = false
            }
        } catch {
            print("Error loading posts: \(error)")
            await MainActor.run {
                self.isLoading = false
            }
        }
        
        // Load real followers for social proof
        do {
            let realFollowers = try await SupabaseClient.shared.fetchFollowers(of: user.id)
            await MainActor.run {
                self.followersList = realFollowers
            }
        } catch {
             print("Error loading real followers: \(error)")
        }
        
        // Load Stories
        do {
            let stories = try await SupabaseClient.shared.fetchUserStories(userId: user.id)
            await MainActor.run {
                self.userStories = stories
                self.hasActiveStories = !stories.isEmpty
            }
        } catch {
             print("Error loading stories: \(error)")
        }
    }
    
    private func toggleFollow() {
        Task {
            do {
                if isFollowing {
                    // Unfollow
                    try await SupabaseClient.shared.unfollowUser(targetId: user.id)
                    await MainActor.run {
                        isFollowing = false
                    }
                } else {
                    try await SupabaseClient.shared.followUser(targetId: user.id)
                    await MainActor.run {
                        isFollowing = true
                    }
                }
            } catch {
                print("Error toggling follow: \(error)")
            }
        }
    }
    
    private func sendFriendRequest() {
        Task {
            do {
                try await SupabaseClient.shared.sendFriendRequest(targetId: user.id)
                await MainActor.run {
                    friendshipStatus = "pendiente"
                }
            } catch {
                print("Error sending friend request: \(error)")
            }
        }
    }
}

// MARK: - Supporting Views

struct StatView: View {
    let count: Int
    let label: String
    
    var body: some View {
        VStack(spacing: 2) {
            Text("\(count)")
                .font(.system(size: 18, weight: .bold))
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(.secondary)
        }
    }
}



struct TabButton: View {
    let icon: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.system(size: 24))
                .foregroundColor(isSelected ? .primary : .gray)
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .overlay(
                    Rectangle()
                        .fill(isSelected ? Color.primary : Color.clear)
                        .frame(height: 2),
                    alignment: .bottom
                )
        }
    }
}

struct PostGridItem: View {
    let post: PostDTO
    
    var body: some View {
        AsyncImage(url: URL(string: post.media_url)) { phase in
            switch phase {
            case .empty:
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .aspectRatio(1, contentMode: .fill)
            case .success(let image):
                image
                    .resizable()
                    .aspectRatio(1, contentMode: .fill)
            case .failure:
                Rectangle()
                    .fill(Color.gray.opacity(0.2))
                    .aspectRatio(1, contentMode: .fill)
                    .overlay(
                        Image(systemName: "photo")
                            .foregroundColor(.gray)
                    )
            @unknown default:
                EmptyView()
            }
        }
        .clipped()
    }
}


