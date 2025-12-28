//
//  SocialFeedView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/23/24.
//

import SwiftUI
import AVKit

struct SocialFeedView: View {
    @EnvironmentObject var appManager: AppManager
    @State private var searchText = ""
    @State private var selectedFilter = "For You"
    
    // Search States
    @State private var isSearching = false
    @State private var searchResults: [ProfileDTO] = []
    
    // Tab Data States
    @State private var friends: [ProfileDTO] = []
    @State private var inboxThreads: [ProfileDTO] = []
    @State private var suggestedUsers: [ProfileDTO] = []
    @State private var posts: [PostDTO] = [] // Real posts
    @State private var reels: [PostDTO] = [] // Reels (videos)
    @State private var isLoadingTab = false
    
    // Logic States
    @State private var showCreatePost = false
    @State private var showFriendsDetail = false
    
    let filters = ["For You", "Reels", "Mi Red", "Grupos", "Mensajes"]
    
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            Color.black.ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Header (Search + Filters)
                VStack(spacing: 12) {
                    // Search Bar
                    if selectedFilter == "Mi Red" {
                        HStack {
                            Image(systemName: "magnifyingglass")
                                .foregroundColor(.gray)
                            
                            TextField("Buscar personas...", text: $searchText)
                                .onSubmit { performSearch() }
                                .submitLabel(.search)
                            
                            if !searchText.isEmpty {
                                Button(action: {
                                    searchText = ""
                                    isSearching = false
                                    searchResults = []
                                }) {
                                    Image(systemName: "xmark.circle.fill")
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                        .padding(12)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(25)
                        .padding(.horizontal)
                    }
                    
                    // Icon Toolbar (Filters)
                    HStack(spacing: 0) {
                        ForEach(filters, id: \.self) { filter in
                            Spacer()
                            
                            Button(action: {
                                appManager.playHaptic()
                                withAnimation { selectedFilter = filter }
                            }) {
                                VStack(spacing: 4) {
                                    Image(systemName: getIconForFilter(filter))
                                        .font(.system(size: 24))
                                        .foregroundColor(selectedFilter == filter ? LyrionTheme.primaryPurple : .gray)
                                }
                                .frame(width: 48, height: 48)
                                .background(selectedFilter == filter ? LyrionTheme.primaryPurple.opacity(0.1) : Color.clear)
                                .clipShape(Circle())
                            }
                            
                            Spacer()
                        }
                    }
                    .padding(.vertical, 8)
                    .padding(.bottom, 10)
                }
                .background(.ultraThinMaterial)
                .padding(.top, 1)
                
                // Content Body
                // Content Body
                VStack(spacing: 0) {
                    
                    if isSearching || !searchText.isEmpty {
                        ScrollView {
                            SearchResultsSection(results: searchResults, isSearching: isSearching)
                        }
                    } else {
                        switch selectedFilter {
                        case "For You":
                            ScrollView {
                                ForYouView(suggestedUsers: suggestedUsers, posts: posts, isLoading: isLoadingTab, friends: friends)
                                    .padding(.bottom, 100) // Space for TabBar
                            }
                            .refreshable {
                                await loadTabData()
                            }
                            
                        case "Reels":
                            // Full screen Reels View (No ScrollView wrapper)
                            ReelsView(reels: reels, isLoading: isLoadingTab, onCreateReel: { showCreatePost = true })
                            
                        case "Mi Red":
                            // New Template: Stories + Ver Mi Red Button + Discovery Cards
                            ScrollView {
                                VStack(spacing: 0) {
                                    // Stories Section
                                    StoriesSection(suggestedUsers: suggestedUsers, usersWithStories: usersWithStories, hasMyStories: hasMyStories)
                                        .padding(.bottom, 20)
                                    
                                    // Ver Mi Red Button
                                    NavigationLink(destination: FriendsDetailView(
                                        pendingRequests: pendingRequests,
                                        outgoingRequests: outgoingRequests,
                                        followingUsers: followingUsers,
                                        followers: followers,
                                        mutualFriends: mutualFriends,
                                        suggestedUsers: suggestedUsers,
                                        isLoading: isLoadingTab,
                                        onRefresh: { Task { await loadTabData() } }
                                    )) {
                                        HStack(spacing: 12) {
                                            Image(systemName: "person.3.fill")
                                                .font(.title3)
                                            Text("Ver mi red")
                                                .font(.headline)
                                                .fontWeight(.bold)
                                        }
                                        .foregroundColor(.white)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 14)
                                        .background(
                                            LinearGradient(
                                                colors: [LyrionTheme.primaryPurple, LyrionTheme.primaryPurple.opacity(0.8)],
                                                startPoint: .leading,
                                                endPoint: .trailing
                                            )
                                        )
                                        .cornerRadius(16)
                                        .shadow(color: LyrionTheme.primaryPurple.opacity(0.3), radius: 8, y: 4)
                                    }
                                    .padding(.horizontal, 20)
                                    .padding(.bottom, 24)
                                    
                                    // Discovery Section
                                    DiscoverySection(users: suggestedUsers)
                                }
                                .padding(.top, 16)
                            }
                            .refreshable {
                                await loadTabData()
                            }
                            
                        case "Mensajes":
                            InboxView(threads: inboxThreads, isLoading: isLoadingTab)
                            
                        case "Grupos":
                            GroupListView()

                        default:
                            EmptyView()
                        }
                    }
                }
                Spacer(minLength: 0)
            }
            
            // Floating Action Button (FAB) for Creating Post
            if selectedFilter == "For You" {
                Button(action: { showCreatePost = true }) {
                    Image(systemName: "plus")
                        .font(.title)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(width: 60, height: 60)
                        .background(LyrionTheme.primaryPurple)
                        .clipShape(Circle())
                        .shadow(radius: 4, y: 3)
                }
                .padding()
                .padding(.bottom, (selectedFilter == "For You") ? 10 : 60) // Lower pos for For You (no TabBar)
            }
        }
        .background(Color.black.ignoresSafeArea())
        .fullScreenCover(isPresented: $showCreatePost, onDismiss: {
            Task { await loadTabData() } // Refresh after posting
        }) {
            CreatePostView()
        }
        .onChange(of: selectedFilter) { oldValue, newValue in
            withAnimation {
                // Hide TabBar for Reels and For You
                appManager.isFullScreenMode = (newValue == "Reels" || newValue == "For You")
            }
            Task { await loadTabData() }
        }
        .task {
            // Set initial state
            if selectedFilter == "Reels" || selectedFilter == "For You" {
                appManager.isFullScreenMode = true
            }
            await loadTabData()
        }
        .onDisappear {
            appManager.isFullScreenMode = false
        }
    }
    
    // New State for Friends Tab
    @State private var pendingRequests: [(requestId: UUID, user: ProfileDTO)] = []
    @State private var outgoingRequests: [(requestId: UUID, user: ProfileDTO)] = []
    @State private var followingUsers: [ProfileDTO] = []
    @State private var followers: [ProfileDTO] = []
    @State private var mutualFriends: [ProfileDTO] = []
    @State private var usersWithStories: [ProfileDTO] = [] // Users who have active stories
    @State private var hasMyStories: Bool = false
    
    private func loadTabData() async {
        isLoadingTab = true
        
        // 1. Fetch Suggestions (Fail silently if offline)
        if suggestedUsers.isEmpty {
            do {
                let available = try await SupabaseClient.shared.fetchAvailableUsers()
                await MainActor.run { self.suggestedUsers = available }
            } catch {
                // Ignore cancelled requests (error -999)
                let nsError = error as NSError
                if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
                    print("ℹ️ Fetch suggestions cancelled (user navigated away)")
                } else {
                    print("Warning: Failed to fetch suggested users: \(error)")
                }
            }
        }
        
        // 2. Fetch Main Content based on Tab
        do {
            switch selectedFilter {
            case "For You":
                // Fetch recent posts from everyone (Has offline fallback)
                // Force empty first if we want to visually prove reload? No, that's annoying.
                // Just fetch.
                let allPosts = try await SupabaseClient.shared.fetchPosts()
                await MainActor.run { 
                    self.posts = allPosts 
                }
                
            case "Reels":
                // Fetch reels (videos con content_type 'reel' o 'both')
                let fetchedReels = try await SupabaseClient.shared.fetchReels()
                await MainActor.run { self.reels = fetchedReels }
                
            case "Mi Red":
                print("🔄 [MI_RED] Cargando datos de Mi Red...")
                async let incomingRequestsArg = SupabaseClient.shared.fetchIncomingFriendRequests()
                async let outgoingRequestsArg = SupabaseClient.shared.fetchOutgoingFriendRequests()
                async let followingArg = SupabaseClient.shared.fetchFollowingUsers()
                async let followersArg = SupabaseClient.shared.fetchFollowers()
                async let mutualArg = SupabaseClient.shared.fetchMutualFriends()
                
                let (incoming, outgoing, following, followers, mutual) = try await (incomingRequestsArg, outgoingRequestsArg, followingArg, followersArg, mutualArg)
                
                // Fetch Stories separately to avoid blocking main content if it takes longer, or just sequential here for simplicity
                print("🔄 [MI_RED] Cargando historias de amigos...")
                let stories = try await SupabaseClient.shared.fetchFriendsStories()
                
                // Fetch My Own Stories to update "Mi Estado" ring
                let myStories = try await SupabaseClient.shared.fetchMyActiveStories()
                
                // Process unique users from stories
                let usersWithStoriesMap = Dictionary(grouping: stories, by: { $0.userId })
                let usersWithStoriesIDs = Set(usersWithStoriesMap.keys)
                let storiesUsers = following.filter { usersWithStoriesIDs.contains($0.id) }
                
                print("✅ [MI_RED] Datos cargados: \(incoming.count) solicitudes recibidas, \(outgoing.count) enviadas, \(following.count) siguiendo, \(followers.count) seguidores, \(mutual.count) amigos")
                
                await MainActor.run { 
                    self.pendingRequests = incoming
                    self.outgoingRequests = outgoing
                    self.followingUsers = following
                    self.followers = followers
                    self.mutualFriends = mutual
                    self.friends = following // Keep synced for Feed logic
                    self.usersWithStories = storiesUsers
                    self.hasMyStories = !myStories.isEmpty // Update local state for UI
                }
                
            case "Mensajes":
                let threads = try await SupabaseClient.shared.fetchInbox()
                await MainActor.run { self.inboxThreads = threads }
                
            default: break
            }
        } catch {
            // Check if it's a cancelled request (error -999)
            let nsError = error as NSError
            if nsError.domain == NSURLErrorDomain && nsError.code == NSURLErrorCancelled {
                print("ℹ️ [MI_RED] Request cancelled (user navigated away)")
            } else {
                print("❌ [MI_RED] Error loading tab content: \(error)")
            }
        }
        
        await MainActor.run { isLoadingTab = false }
    }
    
    private func performSearch() {
        guard !searchText.isEmpty else { return }
        isSearching = true
        appManager.playHaptic()
        
        Task {
            do {
                let results = try await SupabaseClient.shared.searchProfiles(query: searchText)
                await MainActor.run {
                    self.searchResults = results
                    self.isSearching = false
                }
            } catch {
                print("Error searching: \(error)")
                await MainActor.run { self.isSearching = false }
            }
        }
    }
    
    private func getIconForFilter(_ filter: String) -> String {
        switch filter {
        case "For You": return "sparkles"
        case "Reels": return "film" // Alternativas: movieclapper, play.rectangle
        case "Mi Red": return "person.2.fill"
        case "Mensajes": return "bubble.left.and.bubble.right.fill"
        case "Grupos": return "person.3.fill"
        default: return "circle"
        }
    }
}

// MARK: - Tab Views

struct ForYouView: View {
    let suggestedUsers: [ProfileDTO]
    let posts: [PostDTO]
    let isLoading: Bool
    // Add access to friends list for Follow check
    var friends: [ProfileDTO] = []
    
    var body: some View {
        VStack(spacing: 20) {
            if isLoading {
                ProgressView().padding()
            } else if posts.isEmpty {
                // Show empty state or still show suggested
                VStack {
                    Image(systemName: "photo.on.rectangle.angled")
                    .font(.largeTitle)
                    .foregroundColor(.gray)
                    .padding()
                    Text("No hay publicaciones aún")
                    .foregroundColor(.secondary)
                }
                .padding(.vertical, 40)
            } else {
                ForEach(posts) { post in
                    SocialPostView(post: post, friends: friends)
                }
            }
            
            // Suggested People Section
            if !suggestedUsers.isEmpty {
                VStack(alignment: .leading) {
                    Text("Sugerencias para ti")
                        .font(.headline)
                        .padding(.horizontal)
                        .padding(.top)
                    
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 15) {
                            ForEach(suggestedUsers) { user in
                                NavigationLink(destination: UserProfileView(user: user)) {
                                    SuggestedUserCard(user: user)
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                }
                .padding(.vertical)
                .background(Color(UIColor.secondarySystemBackground).opacity(0.5))
            }
        }
        .padding(.top)
    }
}

struct FriendsView: View {
    let pendingRequests: [(requestId: UUID, user: ProfileDTO)]
    let outgoingRequests: [(requestId: UUID, user: ProfileDTO)]
    let followingUsers: [ProfileDTO]
    let followers: [ProfileDTO]
    let mutualFriends: [ProfileDTO]
    let suggestedUsers: [ProfileDTO]
    let isLoading: Bool
    let onRefresh: () -> Void
    
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                
                // Main Header: Conexiones
                HStack {
                    Text("Conexiones")
                        .font(.title2)
                        .fontWeight(.bold)
                        .foregroundColor(.primary)
                }
                .padding(.horizontal)
                .padding(.vertical, 16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color(UIColor.systemBackground))
                
                // 1. Solicitudes Recibidas Section
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("Solicitudes Recibidas (\(pendingRequests.count))")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                        Spacer()
                        if !pendingRequests.isEmpty {
                            Text("Gestionar")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(LyrionTheme.primaryPurple)
                        }
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                    .background(Color(UIColor.secondarySystemBackground).opacity(0.8))
                    
                    if pendingRequests.isEmpty {
                        Text("No hay solicitudes recibidas")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else {
                        ForEach(pendingRequests, id: \.requestId) { item in
                            FriendRequestRow(requestId: item.requestId, senderId: item.user.id, user: item.user)
                            Divider().padding(.leading)
                        }
                    }
                }
                
                // 2. Solicitudes Enviadas Section
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text("Solicitudes Enviadas (\(outgoingRequests.count))")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(UIColor.secondarySystemBackground).opacity(0.8))
                    
                    if outgoingRequests.isEmpty {
                        Text("No hay solicitudes enviadas")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else {
                        ForEach(outgoingRequests, id: \.requestId) { item in
                            OutgoingRequestRow(requestId: item.requestId, user: item.user)
                            Divider().padding(.leading)
                        }
                    }
                }
                
                // 3. Amigos Section
                VStack(alignment: .leading, spacing: 0) {
                     HStack {
                        Text("Amigos")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(UIColor.secondarySystemBackground).opacity(0.8))
                    
                    if mutualFriends.isEmpty {
                        Text("Aún no tienes amigos")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else {
                        ForEach(mutualFriends) { user in
                            MutualFriendRow(user: user, onRefresh: onRefresh)
                            Divider().padding(.leading)
                        }
                    }
                }
                
                // 4. Siguiendo Section
                VStack(alignment: .leading, spacing: 0) {
                     HStack {
                        Text("Siguiendo")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(UIColor.secondarySystemBackground).opacity(0.8))
                    
                    if followingUsers.isEmpty {
                        Text("No sigues a nadie aún")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else {
                        ForEach(followingUsers) { user in
                            FollowingUserRow(user: user, onRefresh: onRefresh)
                            Divider().padding(.leading)
                        }
                    }
                }
                
                // 5. Seguidores Section
                VStack(alignment: .leading, spacing: 0) {
                     HStack {
                        Text("Seguidores")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(UIColor.secondarySystemBackground).opacity(0.8))
                    
                    if followers.isEmpty {
                        Text("Aún no tienes seguidores")
                            .font(.subheadline)
                            .foregroundColor(.gray)
                            .padding()
                            .frame(maxWidth: .infinity, alignment: .center)
                    } else {
                        ForEach(followers) { user in
                            NavigationLink(destination: UserProfileView(user: user)) {
                                FollowerRow(user: user, onRefresh: onRefresh)
                            }
                            Divider().padding(.leading)
                        }
                    }
                }
                
                // 6. Descubrir Personas Section
                VStack(alignment: .leading, spacing: 0) {
                     HStack {
                        Text("Descubrir personas")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.secondary)
                            .textCase(.uppercase)
                    }
                    .padding(.horizontal)
                    .padding(.vertical, 12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color(UIColor.secondarySystemBackground).opacity(0.8))
                    
                    ForEach(suggestedUsers) { user in
                         NavigationLink(destination: UserProfileView(user: user)) {
                            DiscoverUserRow(user: user, onRefresh: onRefresh)
                        }
                        Divider().padding(.leading)
                    }
                }
                
                // Padding bottom for navigation
                Color.clear.frame(height: 100)
            }
        }
        .background(Color(UIColor.systemBackground))
    }
}

// MARK: - Friends Detail View (Navigable)
struct FriendsDetailView: View {
    let pendingRequests: [(requestId: UUID, user: ProfileDTO)]
    let outgoingRequests: [(requestId: UUID, user: ProfileDTO)]
    let followingUsers: [ProfileDTO]
    let followers: [ProfileDTO]
    let mutualFriends: [ProfileDTO]
    let suggestedUsers: [ProfileDTO]
    let isLoading: Bool
    let onRefresh: () -> Void
    
    var body: some View {
        FriendsView(
            pendingRequests: pendingRequests,
            outgoingRequests: outgoingRequests,
            followingUsers: followingUsers,
            followers: followers,
            mutualFriends: mutualFriends,
            suggestedUsers: suggestedUsers,
            isLoading: isLoading,
            onRefresh: onRefresh
        )
        .navigationTitle("Mi Red")
        .navigationBarTitleDisplayMode(.large)
    }
}



// MARK: - Helper Rows

struct FriendRequestRow: View {
    let requestId: UUID
    let senderId: UUID
    let user: ProfileDTO
    @State private var didAction = false
    
    var body: some View {
        if !didAction {
            HStack(alignment: .top, spacing: 12) {
                // Avatar
                AvatarView(url: user.avatarUrl, name: user.username, size: 48)
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(user.username ?? "Usuario")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                            
                            Text("2 amigos en común") // Placeholder for mutuals
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Text("2h") // Placeholder time
                            .font(.caption2)
                            .foregroundColor(.secondary)
                    }
                    
                    HStack(spacing: 8) {
                        Button(action: { acceptRequest() }) {
                            Text("Aceptar")
                                .font(.system(size: 12, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                                .background(LyrionTheme.primaryPurple)
                                .foregroundColor(.white)
                                .cornerRadius(6)
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        Button(action: { rejectRequest() }) {
                            Text("Rechazar")
                                .font(.system(size: 12, weight: .semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                                .background(Color(UIColor.systemGray5))
                                .foregroundColor(.primary)
                                .cornerRadius(6)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    .padding(.top, 4)
                }
            }
            .padding()
            .background(Color(UIColor.secondarySystemBackground).opacity(0.3))
        }
    }
    
    func acceptRequest() {
        Task {
            try? await SupabaseClient.shared.acceptFriendRequest(requestId: requestId, senderId: senderId)
            withAnimation { didAction = true }
        }
    }
    
    func rejectRequest() {
        Task {
            try? await SupabaseClient.shared.rejectFriendRequest(requestId: requestId)
            withAnimation { didAction = true }
        }
    }
}

struct OutgoingRequestRow: View {
    let requestId: UUID
    let user: ProfileDTO
    @State private var didCancel = false
    
    var body: some View {
        if !didCancel {
            HStack(spacing: 12) {
                AvatarView(url: user.avatarUrl, name: user.username, size: 48)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(user.username ?? "Usuario")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.primary)
                    Text("Solicitud pendiente")
                        .font(.caption)
                        .foregroundColor(.orange)
                }
                
                Spacer()
                
                Button(action: { cancelRequest() }) {
                    Text("Cancelar")
                        .font(.system(size: 12, weight: .semibold))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color(UIColor.systemGray5))
                        .foregroundColor(.primary)
                        .cornerRadius(6)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding()
            .background(Color(UIColor.secondarySystemBackground).opacity(0.3))
        }
    }
    
    func cancelRequest() {
        Task {
            // Use rejectFriendRequest to delete the request (sender cancelling)
            try? await SupabaseClient.shared.rejectFriendRequest(requestId: requestId)
            withAnimation { didCancel = true }
        }
    }
}

struct FollowerRow: View {
    let user: ProfileDTO
    let onRefresh: () -> Void
    @State private var isFollowing = false
    @State private var friendRequestSent = false
    
    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(destination: UserProfileView(user: user)) {
                HStack(spacing: 12) {
                    AvatarView(url: user.avatarUrl, name: user.username, size: 48)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(user.username ?? "Usuario")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.primary)
                        Text("Te sigue")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            
            Spacer()
            
            // Botón Seguir
            Button(action: {
                Task {
                    do {
                        try await SupabaseClient.shared.followUser(targetId: user.id)
                        print("✅ [UI] Usuario seguido exitosamente")
                        // Primero recargar datos del servidor
                        onRefresh()
                        // Pequeña espera para que la UI se actualice
                        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 segundos
                        // Luego actualizar estado local
                        withAnimation { isFollowing = true }
                    } catch {
                        print("❌ [UI] Error en FollowerRow.followUser: \(error)")
                    }
                }
            }) {
                Text(isFollowing ? "Siguiendo" : "Seguir")
                    .font(.system(size: 12, weight: .semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(isFollowing ? Color.gray.opacity(0.2) : LyrionTheme.primaryPurple)
                    .foregroundColor(isFollowing ? .primary : .white)
                    .cornerRadius(6)
            }
            .disabled(isFollowing)
            .buttonStyle(PlainButtonStyle())
            
            // Botón Solicitar Amistad
            Button(action: {
                Task {
                    do {
                        try await SupabaseClient.shared.sendFriendRequest(targetId: user.id)
                        print("✅ [UI] Solicitud de amistad enviada")
                        // Primero recargar datos del servidor
                        onRefresh()
                        // Pequeña espera para que la UI se actualice
                        try? await Task.sleep(nanoseconds: 500_000_000) // 0.5 segundos
                        // Luego actualizar estado local
                        withAnimation { friendRequestSent = true }
                    } catch {
                        print("❌ [UI] Error en FollowerRow.sendFriendRequest: \(error)")
                    }
                }
            }) {
                Image(systemName: friendRequestSent ? "checkmark" : "person.badge.plus")
                    .font(.system(size: 14, weight: .semibold))
                    .padding(8)
                    .background(friendRequestSent ? Color.green.opacity(0.2) : Color.blue.opacity(0.1))
                    .foregroundColor(friendRequestSent ? .green : .blue)
                    .cornerRadius(6)
            }
            .disabled(friendRequestSent)
            .buttonStyle(PlainButtonStyle())
        }
        .padding()
        .contentShape(Rectangle())
    }
}

struct MutualFriendRow: View {
    let user: ProfileDTO
    let onRefresh: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(destination: UserProfileView(user: user)) {
                HStack(spacing: 12) {
                    AvatarView(url: user.avatarUrl, name: user.username, size: 48)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(user.username ?? "Usuario")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.primary)
                        Text("Amigos")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            
            Spacer()
            
            Menu {
                Button(role: .destructive, action: {
                    Task {
                        try? await SupabaseClient.shared.removeFriend(targetId: user.id)
                        onRefresh()
                    }
                }) {
                    Label("Eliminar amigo", systemImage: "person.badge.minus")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .foregroundColor(.gray)
                    .padding(8)
                    .contentShape(Rectangle())
            }
        }
        .padding()
    }
}

struct FollowingUserRow: View {
    let user: ProfileDTO
    let onRefresh: () -> Void
    @State private var friendRequestSent = false
    
    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(destination: UserProfileView(user: user)) {
                HStack(spacing: 12) {
                    AvatarView(url: user.avatarUrl, name: user.username, size: 48)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(user.username ?? "Usuario")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.primary)
                        Text("Siguiendo") 
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
            
            Spacer()
            
            HStack(spacing: 0) {
                // Botón Solicitar Amistad
                Button(action: {
                Task {
                    do {
                        try await SupabaseClient.shared.sendFriendRequest(targetId: user.id)
                        print("✅ [UI] Solicitud de amistad enviada desde FollowingUserRow")
                        onRefresh()
                        try? await Task.sleep(nanoseconds: 500_000_000)
                        withAnimation { friendRequestSent = true }
                    } catch {
                        print("❌ [UI] Error en FollowingUserRow.sendFriendRequest: \(error)")
                    }
                }
            }) {
                Image(systemName: friendRequestSent ? "checkmark" : "person.badge.plus")
                    .font(.system(size: 14, weight: .semibold))
                    .padding(8)
                    .background(friendRequestSent ? Color.green.opacity(0.2) : Color.blue.opacity(0.1))
                    .foregroundColor(friendRequestSent ? .green : .blue)
                    .cornerRadius(6)
            }
            .disabled(friendRequestSent)
            .buttonStyle(PlainButtonStyle())
            
            Menu {
                Button(role: .destructive, action: {
                    Task {
                        try? await SupabaseClient.shared.unfollowUser(targetId: user.id)
                        onRefresh()
                    }
                }) {
                    Label("Dejar de seguir", systemImage: "person.badge.minus")
                }
            } label: {
                Image(systemName: "ellipsis")
                    .foregroundColor(.gray)
                    .padding(8)
                    .contentShape(Rectangle())
            }
            .padding(.leading, 8)
        }
        .padding()
        .contentShape(Rectangle())
    }
}
}

struct DiscoverUserRow: View {
    let user: ProfileDTO
    let onRefresh: () -> Void
    @State private var isFollowing = false
    @State private var friendRequestSent = false
    
    var body: some View {
        HStack(spacing: 12) {
            NavigationLink(destination: UserProfileView(user: user)) {
                HStack(spacing: 12) {
                    AvatarView(url: user.avatarUrl, name: user.username, size: 48)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        if let nombre = user.nombre, !nombre.isEmpty {
                            Text("\(nombre) \(user.apellido ?? "")")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                        } else {
                            Text(user.username ?? "Usuario")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundColor(.primary)
                        }
                        Text("Popular cerca de ti")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
                .contentShape(Rectangle()) // Ensure entire area is tappable
            }
            .buttonStyle(PlainButtonStyle())
            
            Spacer()
            
            // Botón Seguir
            Button(action: {
                Task {
                    do {
                        try await SupabaseClient.shared.followUser(targetId: user.id)
                        print("✅ [UI] Usuario seguido desde DiscoverUserRow")
                        onRefresh()
                        try? await Task.sleep(nanoseconds: 500_000_000)
                        withAnimation { isFollowing = true }
                    } catch {
                        print("❌ [UI] Error en DiscoverUserRow.followUser: \(error)")
                    }
                }
            }) {
                Text(isFollowing ? "Siguiendo" : "Seguir")
                    .font(.system(size: 12, weight: .semibold))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(isFollowing ? Color.gray.opacity(0.2) : LyrionTheme.primaryPurple)
                    .foregroundColor(isFollowing ? .primary : .white)
                    .cornerRadius(6)
            }
            .disabled(isFollowing)
            .buttonStyle(PlainButtonStyle())
            
            // Botón Solicitar Amistad
            Button(action: {
                Task {
                    do {
                        try await SupabaseClient.shared.sendFriendRequest(targetId: user.id)
                        print("✅ [UI] Solicitud enviada desde DiscoverUserRow")
                        onRefresh()
                        try? await Task.sleep(nanoseconds: 500_000_000)
                        withAnimation { friendRequestSent = true }
                    } catch {
                        print("❌ [UI] Error en DiscoverUserRow.sendFriendRequest: \(error)")
                    }
                }
            }) {
                Image(systemName: friendRequestSent ? "checkmark" : "person.badge.plus")
                    .font(.system(size: 14, weight: .semibold))
                    .padding(8)
                    .background(friendRequestSent ? Color.green.opacity(0.2) : Color.blue.opacity(0.1))
                    .foregroundColor(friendRequestSent ? .green : .blue)
                    .cornerRadius(6)
            }
            .disabled(friendRequestSent)
            .buttonStyle(PlainButtonStyle())
        }
        .padding()
    }
}


struct InboxView: View {
    let threads: [ProfileDTO]
    let isLoading: Bool
    
    var body: some View {
        VStack {
            if isLoading {
                Spacer()
                ProgressView()
                    .scaleEffect(1.2)
                Spacer()
            } else if threads.isEmpty {
                Spacer()
                VStack(spacing: 20) {
                    Image(systemName: "bubble.left.and.bubble.right")
                        .font(.system(size: 60))
                        .foregroundColor(.gray.opacity(0.5))
                    Text("Aún no has recibido mensajes")
                        .font(.headline)
                        .foregroundColor(.gray)
                    Text("Cuando alguien te envíe un mensaje, aparecerá aquí")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 40)
                }
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(threads) { sender in
                            NavigationLink(destination: DirectChatView(targetUser: sender)) {
                                HStack(spacing: 15) {
                                    AvatarView(url: sender.avatarUrl, name: sender.username, size: 50)
                                    VStack(alignment: .leading, spacing: 4) {
                                        if let nombre = sender.nombre, !nombre.isEmpty {
                                            Text(nombre + " " + (sender.apellido ?? "")).font(.headline).foregroundColor(.primary)
                                            Text(sender.username ?? "Usuario").font(.caption).foregroundColor(.secondary)
                                        } else {
                                            Text(sender.username ?? "Usuario").font(.headline).foregroundColor(.primary)
                                        }
                                        Text("Toca para ver la conversación").font(.subheadline).foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").foregroundColor(.gray).font(.caption)
                                }
                                .padding()
                                .background(Color(UIColor.secondarySystemBackground))
                                .cornerRadius(12)
                                .padding(.horizontal)
                                .padding(.vertical, 4)
                            }
                        }
                    }
                    .padding(.top)
                    .padding(.bottom, 100) // Space for TabBar
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.black)
    }
}

// MARK: - Post View (Real Data)

struct SocialPostView: View {
    let post: PostDTO
    var friends: [ProfileDTO] = [] // Passed from parent
    @EnvironmentObject var appManager: AppManager
    @State private var showComments = false
    @State private var relationshipStatus: String = "none" // "none", "siguiendo", "pendiente", "amigos"
    
    private func followUser() async {
        relationshipStatus = "siguiendo" // Optimistic
        appManager.playHaptic()
        do {
            try await SupabaseClient.shared.followUser(targetId: post.creator_id)
        } catch {
            print("Error following: \(error)")
            relationshipStatus = "none"
        }
    }
    
    private func sendFriendRequest() async {
        relationshipStatus = "pendiente" // Optimistic
        appManager.playHaptic()
        do {
            try await SupabaseClient.shared.sendFriendRequest(targetId: post.creator_id)
        } catch {
             print("Error requesting friend: \(error)")
             // Revert logic is complex (was it following?), simplified revert:
             relationshipStatus = "siguiendo" 
        }
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // 1. Header (User Info)
            // 1. Header (User Info)
            HStack(alignment: .top) { // Align top for better layout with extra text
                if post.is_anonymous == true {
                    Image(systemName: "person.circle.fill")
                        .resizable()
                        .frame(width: 40, height: 40)
                        .foregroundColor(.gray)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Anónimo")
                            .font(.system(size: 14, weight: .semibold))
                        Text(post.created_at.formatted(.relative(presentation: .named)))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                } else {
                    // Navegable al perfil del creador
                    if let creator = post.creator {
                        NavigationLink(destination: UserProfileView(user: creator.toProfileDTO(id: post.creator_id))) {
                            HStack(spacing: 8) {
                                AvatarView(url: post.creator?.avatar_url, name: post.creator?.username, size: 40)
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack {
                                        Text(formatUsername(post.creator?.username))
                                            .font(.headline)
                                        
                                        if let category = post.category {
                                            Text("• \(category)")
                                                .font(.caption)
                                                .foregroundColor(.secondary)
                                        }
                                        
                                        Text("• \(post.created_at.formatted(.relative(presentation: .named)))")
                                            .font(.caption2)
                                            .foregroundColor(.secondary)
                                    }
                                }
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                    } else {
                        AvatarView(url: post.creator?.avatar_url, name: post.creator?.username, size: 40)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Text(formatUsername(post.creator?.username))
                                    .font(.system(size: 14, weight: .semibold))
                            }
                            
                            Text(post.created_at.formatted(.relative(presentation: .named)))
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                Spacer()
                
                // Menu for Post Owner
                if isPostOwner {
                    Menu {
                        Button(action: { showEditPost = true }) {
                            Label("Editar", systemImage: "pencil")
                        }
                        Button(role: .destructive, action: {
                            itemToDelete = .post
                            showDeleteAlert = true
                        }) {
                            Label("Eliminar", systemImage: "trash")
                        }
                    } label: {
                        Image(systemName: "ellipsis")
                            .foregroundColor(.gray)
                            .padding(8)
                    }
                }
                
                // Follow Button / Friend Request Button
                if let currentUserId = SupabaseClient.shared.loadSession()?.user.id,
                   post.is_anonymous != true,
                   let creatorId = post.creator_id.uuidString.lowercased() as String?,
                   creatorId != currentUserId.lowercased() {
                    
                    // Nueva lógica corregida:
                    // 1. Si 'none': solo mostrar "Seguir"
                    // 2. Si 'siguiendo': mostrar "Siguiendo" (deshabilitado) Y "Solicitar Amistad"
                    // 3. Si 'pendiente': solo mostrar "Solicitud Enviada"
                    // 4. Si 'amigos': no mostrar botones
                    
                    HStack(spacing: 8) {
                        // Follow Button Logic
                        if relationshipStatus == "none" {
                            Button(action: {
                                Task { await followUser() }
                            }) {
                                Text("Seguir")
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(LyrionTheme.primaryPurple)
                                    .foregroundColor(.white)
                                    .cornerRadius(15)
                            }
                        } else if relationshipStatus == "siguiendo" {
                             Text("Siguiendo")
                                .font(.caption)
                                .fontWeight(.bold)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.gray.opacity(0.2))
                                .foregroundColor(.primary)
                                .cornerRadius(15)
                            
                            // Solo mostrar "Solicitar Amistad" cuando ya está siguiendo
                            Button(action: {
                                Task { await sendFriendRequest() }
                            }) {
                                Text("Solicitar Amistad")
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 6)
                                    .background(Color.blue)
                                    .foregroundColor(.white)
                                    .cornerRadius(15)
                            }
                        } else if relationshipStatus == "pendiente" {
                            Text("Solicitud Enviada")
                                .font(.caption)
                                .fontWeight(.bold)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.orange.opacity(0.2))
                                .foregroundColor(.orange)
                                .cornerRadius(15)
                        } else if relationshipStatus == "amigos" {
                            // User wants both "Siguiendo" and "Amigos"
                            Text("Siguiendo")
                               .font(.caption)
                               .fontWeight(.bold)
                               .padding(.horizontal, 12)
                               .padding(.vertical, 6)
                               .background(Color.gray.opacity(0.1))
                               .foregroundColor(.secondary)
                               .cornerRadius(15)
                            
                            Text("Amigos")
                                .font(.caption)
                                .fontWeight(.bold)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Color.green.opacity(0.15))
                                .foregroundColor(.green)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 15)
                                        .stroke(Color.green.opacity(0.3), lineWidth: 1)
                                )
                                .cornerRadius(15)
                        }
                    }
                }
            }
            .padding(12)
            .task {
                // Check relationship on appear
                if let creatorId = post.creator_id.uuidString.lowercased() as String?,
                   let currentUserId = SupabaseClient.shared.loadSession()?.user.id,
                   creatorId != currentUserId.lowercased() {
                       
                    // If passed friends list contains creator, set to amigos? 
                    // To be safe and precise, let's fetch status.
                    // Optimisation: check 'friends' list first.
                    if friends.contains(where: { $0.id.uuidString.lowercased() == creatorId }) {
                        relationshipStatus = "amigos"
                    } else {
                        do {
                            let status = try await SupabaseClient.shared.checkRelationshipStatus(with: post.creator_id)
                            await MainActor.run { self.relationshipStatus = status }
                        } catch {
                            print("Error checking relationship: \(error)")
                        }
                    }
                }
            }
            
            // 2. Caption (Moved Up)
            if let caption = post.caption, !caption.isEmpty {
                Text(caption)
                    .font(.system(size: 15)) // Slightly larger for readability
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 12)
                    .padding(.bottom, 8)
            }
            
            // 3. Media
            if post.isVideo {
                // Video Player
                InlineVideoPlayerView(videoURL: post.media_url, thumbnail: URL(string: post.thumbnail_url ?? ""))
                    .frame(height: 400)
                    .clipped()
            } else {
                // Image
                CachedAsyncImage(url: URL(string: post.media_url)) { image in
                    image.resizable()
                        .aspectRatio(contentMode: .fit)
                } placeholder: {
                    Rectangle()
                        .fill(Color.gray.opacity(0.1))
                        .aspectRatio(4/5, contentMode: .fit)
                        .overlay(ProgressView())
                }
                .frame(maxHeight: 500)
            }
            
            // 4. Stats Bar (Visual reactions & comment counts)
            HStack {
                // Reactions Stack
                Button(action: {
                    if let reactions = post.post_reactions, !reactions.isEmpty {
                        showReactionsList = true
                    }
                }) {
                    HStack(spacing: 4) {
                        if let reactions = post.post_reactions, !reactions.isEmpty {
                            let uniqueEmojis = Array(Set(reactions.map { $0.emoji })).prefix(3)
                            
                            HStack(spacing: -8) {
                                ForEach(Array(uniqueEmojis), id: \.self) { emoji in
                                    ZStack {
                                        Circle().fill(Color.white).frame(width: 20, height: 20)
                                        Text(emoji).font(.system(size: 14))
                                    }
                                }
                            }
                            
                            Text("\(post.reactionCount)")
                                .font(.caption)
                                .foregroundColor(.secondary)
                                .padding(.leading, 6)
                        }
                    }
                }
                .disabled(post.post_reactions?.isEmpty ?? true)
                
                Spacer()
                
                // Comments Count
                if let comments = post.comments, !comments.isEmpty {
                    Button(action: { showComments = true }) {
                        Text("\(comments.count) comentarios")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            
            Divider()
            
            // 5. Action Bar (Buttons)
            HStack {
                // Like / React Button
                // Use computed property handling optimistic state
                
                Menu {
                    Button("❤️ Me encanta") { react("❤️") }
                    Button("😂 Me divierte") { react("😂") }
                    Button("🔥 Fuego") { react("🔥") }
                    Button("😢 Triste") { react("😢") }
                    Button("👍 Me gusta") { react("👍") }
                    
                    Divider()
                    
                    Button(role: .destructive, action: {
                        optimisticReaction = "REMOVE"
                        Task {
                            do {
                                try await SupabaseClient.shared.removeReaction(postId: post.id.uuidString)
                            } catch {
                                await MainActor.run { optimisticReaction = nil }
                            }
                        }
                    }) {
                        Label("Dejar de reaccionar", systemImage: "xmark.circle")
                    }
                } label: {
                    HStack(spacing: 6) {
                        if let activeEmoji = currentReaction {
                            Text(activeEmoji).font(.subheadline)
                            Text(activeEmoji == "👍" ? "Me gusta" : activeEmoji == "❤️" ? "Me encanta" : "Reaccionado")
                                .foregroundColor(LyrionTheme.primaryPurple)
                        } else {
                            Image(systemName: "hand.thumbsup")
                            Text("Me gusta")
                        }
                    }
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(currentReaction != nil ? LyrionTheme.primaryPurple : .secondary)
                    .frame(maxWidth: .infinity)
                } primaryAction: {
                     if currentReaction == nil {
                         react("👍")
                     } else {
                         // If already reacted, do nothing on tap? Or toggle off?
                         // Facebook usually keeps it unless long pressed, or tap to remove.
                         // Let's implement tap to remove/toggle if same.
                         react(currentReaction ?? "👍") 
                     }
                }
                
                // Comment Button
                Button(action: { showComments = true }) {
                    HStack(spacing: 6) {
                        Image(systemName: "bubble.left")
                        Text("Comentar")
                    }
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
                }
                
                // Share Button (Visual only for now)
                Button(action: {
                     appManager.playHaptic()
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrowshape.turn.up.right")
                        Text("Compartir")
                    }
                    .font(.subheadline)
                    .fontWeight(.medium)
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.vertical, 10)
            .padding(.horizontal, 4)
            
        }
        .background(Color(UIColor.secondarySystemBackground))
        .cornerRadius(0) // Facebook style often uses 0 radius for card content if full width, but let's keep rounded for modern feel or match user request? User says "conservando nuestra paleta". We'll keep slight radius.
        .cornerRadius(12) 
        .padding(.horizontal, 0) // Edge to edge looks more "Facebook feed" but user has margin. Let's keep existing margin or reduce it.
        .padding(.vertical, 8)
        .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
        .sheet(isPresented: $showComments) {
            CommentsSheetView(postId: post.id.uuidString)
        }
        .sheet(isPresented: $showReactionsList) {
            if let reactions = post.post_reactions {
                ReactionsListView(reactions: reactions)
            }
        }
        .sheet(isPresented: $showEditPost) {
            EditPostView(post: post) {
                // Trigger refresh if needed
            }
        }
        .alert("¿Eliminar publicación?", isPresented: $showDeleteAlert) {
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) {
                deletePost()
            }
        } message: {
            Text("Esta acción no se puede deshacer.")
        }
    }
    
    @State private var showReactionsList = false
    @State private var showEditPost = false
    @State private var showDeleteAlert = false
    @State private var itemToDelete: DeleteItem?
    
    enum DeleteItem {
        case post
    }
    
    private var isPostOwner: Bool {
        guard let currentUserId = SupabaseClient.shared.loadSession()?.user.id else { return false }
        return post.creator_id.uuidString.lowercased() == currentUserId.lowercased()
    }
    
    // Optimistic State
    @State private var optimisticReaction: String? = nil
    
    // ... (Computed property remains same)
    
    // ... (react func remains same)
    
    // Add sheet modifier at end of body
    // ...
    // Note: I will inject this into the body structure via separate replace call to be safe with indentation
    // Let's modify the body to include the sheet first.

    
    // Computed property to merge real data with optimistic state
    var currentReaction: String? {
        if let opt = optimisticReaction {
             return opt == "REMOVE" ? nil : opt
        }
        return post.post_reactions?.first(where: { 
             $0.user_id.uuidString.lowercased() == SupabaseClient.shared.loadSession()?.user.id.lowercased() 
        })?.emoji
    }

    private func react(_ emoji: String) {
        let previousReaction = currentReaction
        let isRemoving = (previousReaction == emoji)
        
        // Optimistic Update
        if isRemoving {
            // Toggle off
            optimisticReaction = "REMOVE" // Special flag
        } else {
            optimisticReaction = emoji
        }
        
        appManager.playHaptic()
        
        Task {
            do {
                if isRemoving {
                    try await SupabaseClient.shared.removeReaction(postId: post.id.uuidString)
                } else {
                    try await SupabaseClient.shared.reactToPost(postId: post.id.uuidString, emoji: emoji)
                }
                // Success: The parent view will eventually refresh
            } catch {
                // Revert on failure
                print("Error reacting: \(error)")
                await MainActor.run {
                    optimisticReaction = nil // Reset to truth
                }
            }
        }
    }
    
    private func deletePost() {
        Task {
            do {
                try await SupabaseClient.shared.deletePost(postId: post.id.uuidString)
                // Note: The parent view needs to refresh. 
                // In a real app, use a Binding or EnvironmentObject to trigger refresh.
                // For now, we'll just print.
                print("Post deleted (refresh required)")
            } catch {
                print("Error deleting post: \(error)")
            }
        }
    }
    
    private func formatUsername(_ name: String?) -> String {
        // Check for real name if available in post.creator
        if let creator = post.creator,
           let nombre = creator.nombre, !nombre.isEmpty {
            let apellido = creator.apellido ?? ""
            return "\(nombre) \(apellido)".trimmingCharacters(in: .whitespaces)
        }
        
        guard let name = name, !name.isEmpty else { return "Usuario" }
        // 1. If email, take prefix
        let prefix = name.components(separatedBy: "@").first ?? name
        // 2. Take first word (remove last names)
        return prefix.components(separatedBy: " ").first ?? prefix
    }
}

// MARK: - Reaction List View
struct ReactionsListView: View {
    let reactions: [ReactionDTO]
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationView {
            List {
                ForEach(reactions) { reaction in
                     HStack(spacing: 12) {
                        Text(reaction.emoji)
                            .font(.title3)
                        
                        AvatarView(url: reaction.user?.avatar_url, name: reaction.user?.username, size: 40)
                        
                        Text(formatUsername(reaction.user?.username))
                            .font(.headline)
                        
                        Spacer()
                    }
                    .padding(.vertical, 4)
                }
            }
            .listStyle(.plain)
            .navigationTitle("Reacciones")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cerrar") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
    
    private func formatUsername(_ name: String?) -> String {
        guard let name = name, !name.isEmpty else { return "Usuario" }
        let prefix = name.components(separatedBy: "@").first ?? name
        return prefix.components(separatedBy: " ").first ?? prefix
    }
}
// Helpers reused...


struct SearchResultsSection: View {
    let results: [ProfileDTO]
    let isSearching: Bool
    
    var body: some View {
        VStack(alignment: .leading) {
            Text("Resultados")
                .font(.headline)
                .padding(.horizontal)
                .padding(.top)
            
            if isSearching {
                ProgressView().frame(maxWidth: .infinity).padding()
            } else if results.isEmpty {
                Text("No se encontraron usuarios").foregroundColor(.secondary).frame(maxWidth: .infinity).padding()
            } else {
                ForEach(results) { user in
                    NavigationLink(destination: UserProfileView(user: user)) {
                        SearchUserRow(user: user)
                            .padding()
                            .background(Color(UIColor.secondarySystemBackground))
                            .cornerRadius(12)
                            .padding(.horizontal)
                    }
                }
            }
        }
    }
}


// MARK: - Inline Video Player View
struct InlineVideoPlayerView: View {
    let videoURL: String
    let thumbnail: URL?
    
    @StateObject private var playerManager = InlineVideoManager()
    @State private var showPlayButton = true
    
    var body: some View {
        ZStack {
            if let url = URL(string: videoURL) {
                // Video Player
                CustomVideoPlayerView(player: playerManager.player)
                    .onAppear {
                        playerManager.setupPlayer(url: url)
                    }
                    .onDisappear {
                        playerManager.pause()
                    }
                
                // Play/Pause Overlay
                if !playerManager.isPlaying || showPlayButton {
                    Color.black.opacity(0.3)
                        .allowsHitTesting(false)
                    
                    Image(systemName: playerManager.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 60))
                        .foregroundColor(.white)
                        .onAppear {
                            if playerManager.isPlaying {
                                DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                                    withAnimation {
                                        showPlayButton = false
                                    }
                                }
                            }
                        }
                }
            } else {
                // Fallback thumbnail
                if let thumbURL = thumbnail {
                    CachedAsyncImage(url: thumbURL) { image in
                        image.resizable().aspectRatio(contentMode: .fit)
                    } placeholder: {
                        Rectangle().fill(Color.gray.opacity(0.3))
                            .overlay(
                                Image(systemName: "play.circle.fill")
                                    .font(.system(size: 50))
                                    .foregroundColor(.white)
                            )
                    }
                }
            }
        }
        .onTapGesture {
            if playerManager.isPlaying {
                playerManager.pause()
            } else {
                playerManager.play()
            }
            withAnimation {
                showPlayButton = true
            }
        }
    }
}

// MARK: - Inline Video Manager
class InlineVideoManager: ObservableObject {
    @Published var player: AVPlayer?
    @Published var isPlaying: Bool = false
    
    func setupPlayer(url: URL) {
        let playerItem = AVPlayerItem(url: url)
        player = AVPlayer(playerItem: playerItem)
        player?.isMuted = false
        
        // Add observer for video end
        NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: playerItem,
            queue: .main
        ) { [weak self] _ in
            self?.player?.seek(to: .zero)
            self?.isPlaying = false
        }
    }
    
    func play() {
        player?.play()
        isPlaying = true
    }
    
    func pause() {
        player?.pause()
        isPlaying = false
    }
    
    deinit {
        pause()
        player = nil
    }
}

// MARK: - Stories Section
struct StoriesSection: View {
    let suggestedUsers: [ProfileDTO] // Kept for layout fallback if needed, but we prefer `usersWithStories`
    // We add a new property for users who actually have stories
    var usersWithStories: [ProfileDTO] = []
    var hasMyStories: Bool = false
    
    // Logic to open viewer
    @State private var selectedStories: [SupabaseClient.StoryDTO] = []
    @State private var showStoryViewer = false
    
    // Logic for My Story Viewer
    @State private var showMyStoryViewer = false
    @State private var myStories: [SupabaseClient.StoryDTO] = []
    
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Estados")
                    .font(.title2)
                    .fontWeight(.bold)
                Spacer()
                Button(action: {}) {
                    Text("Ver todo")
                        .font(.caption)
                        .fontWeight(.semibold)
                        .foregroundColor(LyrionTheme.primaryPurple)
                }
            }
            .padding(.horizontal, 20)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 16) {
                    // My Story (Add button or View)
                    if hasMyStories {
                         Button {
                             fetchAndShowMyStories()
                         } label: {
                             MyStoryCircle(hasStories: true)
                         }
                         .buttonStyle(PlainButtonStyle())
                    } else {
                        NavigationLink(destination: StoryUploadView()) {
                             MyStoryCircle(hasStories: false)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    
                    // Real Users with Stories
                    if !usersWithStories.isEmpty {
                        ForEach(usersWithStories) { user in
                            Button {
                                openStories(for: user)
                            } label: {
                                StoryCircle(user: user)
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    } else {
                        // Disclaimer or empty state
                         Text("Sin historias recientes")
                            .font(.caption)
                            .foregroundColor(.gray)
                            .padding(.leading, 8)
                    }
                }
                .padding(.horizontal, 20)
            }
        }
        .padding(.vertical, 16)
        .background(Color(UIColor.secondarySystemBackground).opacity(0.3))
        .cornerRadius(24, corners: .bottomLeft)
        .cornerRadius(24, corners: .bottomRight)
        .fullScreenCover(isPresented: $showStoryViewer) {
            StoryViewer(stories: selectedStories)
        }
        .fullScreenCover(isPresented: $showMyStoryViewer) {
            StoryViewer(stories: myStories)
        }
        
    }
    
    private func fetchAndShowMyStories() {
        Task {
            do {
                let stories = try await SupabaseClient.shared.fetchMyActiveStories()
                await MainActor.run {
                    self.myStories = stories
                    if !stories.isEmpty {
                        self.showMyStoryViewer = true
                    }
                }

            } catch {
                print("Error loading my stories: \(error)")
            }
        }
    }
    
    private func openStories(for user: ProfileDTO) {
        Task {
            do {
                let stories = try await SupabaseClient.shared.fetchUserStories(userId: user.id)
                await MainActor.run {
                    self.selectedStories = stories
                    self.showStoryViewer = true
                }
            } catch {
                print("Error fetching stories for user: \(error)")
            }
        }
    }
}

struct MyStoryCircle: View {
    var hasStories: Bool = false
    
    var body: some View {
         VStack(spacing: 8) {
            ZStack(alignment: .bottomTrailing) {
                // Ring
                if hasStories {
                    Circle()
                        .stroke(
                            LinearGradient(
                                colors: [.orange, .pink, .purple],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            ),
                            lineWidth: 3
                        )
                        .frame(width: 72, height: 72)
                } else {
                    Circle()
                        .stroke(style: StrokeStyle(lineWidth: 2, dash: [5]))
                        .foregroundColor(.gray.opacity(0.3))
                        .frame(width: 72, height: 72)
                }
                
                Circle()
                    .fill(Color.gray.opacity(0.1))
                    .frame(width: 68, height: 68)
                
                // Add button (only if no stories, or keep it as overlay?)
                // User might want to ADD even if they HAVE stories.
                // Usually tapping "My Story" with stories opens Viewer. Viewer has "Add" button usually.
                // Let's hide the "plus" if hasStories, or keep it small?
                // Standard behavior: Ring = View. Plus = Add.
                // For now, if hasStories, show Ring. If not, show Plus.
                
                if !hasStories {
                    Circle()
                        .fill(LyrionTheme.primaryPurple)
                        .frame(width: 24, height: 24)
                        .overlay(
                            Image(systemName: "plus")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.white)
                        )
                        .offset(x: 4, y: 4)
                }
            }
            
            Text("Mi Estado")
                .font(.caption)
                .foregroundColor(.secondary)
        }
    }
}

struct StoryCircle: View {
    let user: ProfileDTO
    
    var body: some View {
        VStack(spacing: 8) {
            ZStack {
                // Gradient ring always visible here because this list is ONLY for users with stories
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color.orange, Color.pink, LyrionTheme.primaryPurple],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 72, height: 72)
                
                // Avatar
                AvatarView(url: user.avatarUrl, name: user.username, size: 66)
                    .overlay(
                        Circle()
                            .stroke(Color(UIColor.systemBackground), lineWidth: 2)
                    )
            }
            
            Text(user.username?.components(separatedBy: "@").first?.components(separatedBy: " ").first ?? "Usuario")
                .font(.caption)
                .foregroundColor(.primary)
                .lineLimit(1)
                .frame(width: 72)
        }
    }
}

// MARK: - Discovery Section
struct DiscoverySection: View {
    let users: [ProfileDTO]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Descubre Personas")
                    .font(.title2)
                    .fontWeight(.bold)
                Spacer()
                Button(action: {}) {
                    Image(systemName: "slider.horizontal.3")
                        .font(.system(size: 16))
                        .foregroundColor(.primary)
                        .padding(8)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(10)
                }
            }
            .padding(.horizontal, 20)
            
            LazyVGrid(columns: [
                GridItem(.flexible(), spacing: 12),
                GridItem(.flexible(), spacing: 12)
            ], spacing: 12) {
                ForEach(users.prefix(8)) { user in
                    DiscoveryCard(user: user)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 100) // Space for tab bar
        }
    }
}

struct DiscoveryCard: View {
    let user: ProfileDTO
    @State private var isLiked = false
    @State private var isDismissed = false
    
    var body: some View {
        if !isDismissed {
            GeometryReader { geometry in
                ZStack(alignment: .bottom) {
                    // Profile Image
                    if let avatarUrl = user.avatarUrl, let url = URL(string: avatarUrl) {
                        CachedAsyncImage(url: url) { image in
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                                .frame(width: geometry.size.width, height: geometry.size.height)
                                .clipped()
                        } placeholder: {
                            Rectangle()
                                .fill(Color.gray.opacity(0.2))
                                .overlay(
                                    Image(systemName: "person.fill")
                                        .font(.largeTitle)
                                        .foregroundColor(.gray)
                                )
                        }
                    } else {
                        Rectangle()
                            .fill(Color.gray.opacity(0.2))
                            .overlay(
                                Image(systemName: "person.fill")
                                    .font(.largeTitle)
                                    .foregroundColor(.gray)
                            )
                    }
                    
                    // Gradient overlay
                    LinearGradient(
                        colors: [.clear, .black.opacity(0.3), .black.opacity(0.9)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    
                    // Online badge (top right)
                    VStack {
                        HStack {
                            Spacer()
                            HStack(spacing: 4) {
                                Circle()
                                    .fill(Color.green)
                                    .frame(width: 6, height: 6)
                                Text("Online")
                                    .font(.system(size: 9, weight: .semibold))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(.ultraThinMaterial)
                            .cornerRadius(12)
                            .padding(12)
                        }
                        Spacer()
                    }
                    
                    // User info and actions
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(alignment: .firstTextBaseline, spacing: 6) {
                            Text(user.username?.components(separatedBy: "@").first?.components(separatedBy: " ").first ?? "Usuario")
                                .font(.system(size: 17, weight: .bold))
                                .foregroundColor(.white)
                            
                            if let edad = user.edad {
                                Text("\(edad)")
                                    .font(.system(size: 17))
                                    .foregroundColor(.white.opacity(0.9))
                            }
                        }
                        
                        if let bio = user.bio {
                            Text(bio)
                                .font(.caption)
                                .foregroundColor(.white.opacity(0.8))
                                .lineLimit(1)
                        }
                        
                        // Action buttons
                        HStack(spacing: 8) {
                            Button(action: {
                                withAnimation {
                                    isDismissed = true
                                }
                            }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 16))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(.ultraThinMaterial)
                                    .cornerRadius(20)
                            }
                            
                            Button(action: {
                                withAnimation {
                                    isLiked = true
                                }
                            }) {
                                Image(systemName: isLiked ? "heart.fill" : "heart")
                                    .font(.system(size: 16))
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 10)
                                    .background(
                                        LinearGradient(
                                            colors: [LyrionTheme.primaryPurple, LyrionTheme.primaryPurple.opacity(0.8)],
                                            startPoint: .leading,
                                            endPoint: .trailing
                                        )
                                    )
                                    .cornerRadius(20)
                                    .shadow(color: LyrionTheme.primaryPurple.opacity(0.3), radius: 4, y: 2)
                            }
                        }
                    }
                    .padding(12)
                }
            }
            .aspectRatio(3/4.2, contentMode: .fit)
            .cornerRadius(16)
            .shadow(color: .black.opacity(0.1), radius: 4, y: 2)
        }
    }
}

// Helper extension for custom corner radius
extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}
