//
//  ReelsView.swift
//  Lyrion
//
//  Created for Lyrion IA
//

import SwiftUI
import AVKit

struct ReelsView: View {
    let reels: [PostDTO]
    let isLoading: Bool
    let onCreateReel: () -> Void
    @State private var currentIndex: Int = 0
    @EnvironmentObject var appManager: AppManager
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if isLoading {
                ProgressView("Cargando Reels...")
                    .tint(.white)
                    .foregroundColor(.white)
            } else if reels.isEmpty {
                emptyStateView
            } else {
                // Vertical Paging Implementation
                // Vertical Paging Implementation
                GeometryReader { proxy in
                    TabView(selection: $currentIndex) {
                        ForEach(Array(reels.enumerated()), id: \.element.id) { index, reel in
                            ReelItemView(reel: reel, isCurrentReel: currentIndex == index, onCreateReel: onCreateReel)
                                .frame(width: proxy.size.width, height: proxy.size.height)
                                .rotationEffect(.degrees(-90)) // Counter-rotate content
                                .tag(index)
                        }
                    }
                    .rotationEffect(.degrees(90)) // Rotate TabView
                    .frame(width: proxy.size.height, height: proxy.size.width)
                    .position(x: proxy.size.width / 2, y: proxy.size.height / 2)
                    .tabViewStyle(.page(indexDisplayMode: .never))
                }
                .ignoresSafeArea() // Ensure GeometryReader measures FULL screen
            }
        }
        .clipped() // Prevent any bleeding outside bounds
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Image(systemName: "video.slash")
                .font(.system(size: 60))
                .foregroundColor(.gray)
            
            Text("No hay reels disponibles")
                .font(.headline)
                .foregroundColor(.white)
            
            Text("Sé el primero en crear un reel")
                .font(.subheadline)
                .foregroundColor(.gray)
        }
    }
}

// MARK: - Reel Item View
struct ReelItemView: View {
    let reel: PostDTO
    let isCurrentReel: Bool
    let onCreateReel: () -> Void
    @StateObject private var playerManager = VideoPlayerManager()
    @State private var showComments = false
    @State private var isMuted = false
    @State private var showControls = false
    @EnvironmentObject var appManager: AppManager
    
    // Interaction State
    @State private var isLiked: Bool = false
    @State private var likeCount: Int = 0
    @State private var commentCount: Int = 0
    @State private var relationshipStatus: String = "none"
    
    // Edit/Delete State
    @State private var showEditSheet = false
    @State private var showDeleteAlert = false
    @State private var isOwner: Bool = false
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            // Video Player
            if URL(string: reel.media_url) != nil {
                CustomVideoPlayerView(player: playerManager.player)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .onTapGesture {
                        if playerManager.isPlaying {
                            playerManager.pause()
                        } else {
                            playerManager.play()
                        }
                        withAnimation {
                            showControls = true
                        }
                    }
            }
            
            // Gradient Overlay
            LinearGradient(
                colors: [.clear, .black.opacity(0.8)],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            .allowsHitTesting(false)
            
            // Controls Interface
            controlsInterface
            
            // Play/Pause Center Indicator
            if showControls {
                Image(systemName: playerManager.isPlaying ? "pause.fill" : "play.fill")
                    .font(.system(size: 80))
                    .foregroundColor(.white.opacity(0.7))
                    .allowsHitTesting(false)
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.8) {
                            withAnimation { showControls = false }
                        }
                    }
            }
        }
        .onAppear {
            if let url = URL(string: reel.media_url) {
                playerManager.setupPlayer(url: url, shouldLoop: true)
                if isCurrentReel { playerManager.play() }
            }
            initializeState()
        }
        .onDisappear {
            playerManager.pause()
        }
        .onChange(of: isCurrentReel) { oldValue, newValue in
            if newValue {
                playerManager.play()
            } else {
                playerManager.pause()
            }
        }
        .sheet(isPresented: $showComments) {
            CommentsSheetView(postId: reel.id.uuidString, postCreatorId: reel.creator_id.uuidString)
        }
        .sheet(isPresented: $showEditSheet) {
            EditReelSheet(reel: reel)
        }
        .alert("¿Eliminar Reel?", isPresented: $showDeleteAlert) {
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) {
                deleteReel()
            }
        } message: {
            Text("Esta acción no se puede deshacer.")
        }
    }
    
    private var controlsInterface: some View {
        VStack {
            // Header zone (Mute button)
            HStack {
                Spacer()
                Button(action: {
                    isMuted.toggle()
                    playerManager.setMuted(isMuted)
                }) {
                    Image(systemName: isMuted ? "speaker.slash.fill" : "speaker.wave.2.fill")
                        .font(.title3)
                        .foregroundColor(.white)
                        .padding(10)
                        .background(.ultraThinMaterial)
                        .clipShape(Circle())
                }
                .padding(.top, 60)
                .padding(.trailing, 20)
            }
            
            Spacer()
            
            // Footer info
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 10) {
                    // User - Navegable al perfil
                    if let creator = reel.creator {
                        // Crear ProfileDTO temporal para navegación
                        let profileForNav = ProfileDTO(
                            id: reel.creator_id,
                            username: creator.username,
                            nombre: nil,
                            apellido: nil,
                            avatarUrl: creator.avatar_url,
                            bannerUrl: nil,
                            bio: nil,
                            occupation: nil,
                            country: nil,
                            edad: nil,
                            altura: nil,
                            peso: nil,
                            estadoCivil: nil,
                            estadoRegion: nil,
                            isActive: nil,
                            lastActiveAt: nil
                        )
                        NavigationLink(destination: UserProfileView(user: profileForNav)) {
                            HStack {
                                AvatarView(url: reel.creator?.avatar_url, name: reel.creator?.username, size: 36)
                                VStack(alignment: .leading, spacing: 0) {
                                    if let nombre = creator.nombre, !nombre.isEmpty {
                                        Text("\(nombre) \(creator.apellido ?? "")")
                                            .font(.headline)
                                            .foregroundColor(.white)
                                    } else {
                                        Text(reel.creator?.username ?? "Anónimo")
                                            .font(.headline)
                                            .foregroundColor(.white)
                                    }
                                }
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        // Follow Button
                        if let currentUserId = SupabaseClient.shared.loadSession()?.user.id,
                           reel.creator_id.uuidString.lowercased() != currentUserId.lowercased() {
                            
                            if relationshipStatus == "none" {
                                Button(action: followUser) {
                                    Text("Seguir")
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Color.clear)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(Color.white, lineWidth: 1)
                                        )
                                        .foregroundColor(.white)
                                }
                            } else if relationshipStatus == "siguiendo" || relationshipStatus == "amigos" {
                                Text("Siguiendo")
                                    .font(.caption)
                                    .fontWeight(.bold)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(Color.white.opacity(0.2))
                                    .cornerRadius(8)
                                    .foregroundColor(.white)
                            } else if relationshipStatus == "pendiente" {
                                Text("Pendiente")
                                    .font(.caption)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .foregroundColor(.white.opacity(0.8))
                            }
                        }
                    } else {
                        HStack {
                            AvatarView(url: reel.creator?.avatar_url, name: reel.creator?.username, size: 36)
                            Text("Anónimo")
                                .font(.headline)
                                .foregroundColor(.white)
                        }
                    }
                    
                    if let title = reel.title, !title.isEmpty {
                        Text(title)
                            .font(.headline)
                            .bold()
                            .foregroundColor(.white)
                            .lineLimit(1)
                    }
                    
                    if let category = reel.category, !category.isEmpty {
                        Text(category)
                            .font(.caption)
                            .fontWeight(.medium)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(LyrionTheme.primaryPurple)
                            .foregroundColor(.white)
                            .cornerRadius(4)
                    }
                    
                    // Caption
                    if let caption = reel.caption {
                        Text(caption)
                            .font(.subheadline)
                            .foregroundColor(.white)
                            .lineLimit(2)
                    }
                    
                    // Duration tag
                    if let duration = reel.duration_seconds {
                         Text("\(Int(duration))s")
                            .font(.caption2)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(4)
                            .foregroundColor(.white)
                    }
                }
                .padding(.bottom, 40)
                .padding(.leading, 20)
                
                Spacer()
                
                // Sidebar buttons
                VStack(spacing: 20) {
                    // Creation Button (Moved here)
                    SidebarButton(icon: "camera.fill", text: "Crear") {
                        onCreateReel()
                    }
                    
                    SidebarButton(
                        icon: isLiked ? "heart.fill" : "heart",
                        text: "\(likeCount)",
                        color: isLiked ? .red : .white
                    ) {
                        toggleLike()
                    }
                    
                    SidebarButton(icon: "bubble.right.fill", text: "\(commentCount)") {
                        showComments = true
                    }
                    
                    SidebarButton(icon: "paperplane.fill", text: "Share") { }
                    
                    if isOwner {
                        Menu {
                            Button(action: { showEditSheet = true }) {
                                Label("Editar", systemImage: "pencil")
                            }
                            Button(role: .destructive, action: { showDeleteAlert = true }) {
                                Label("Eliminar", systemImage: "trash")
                            }
                        } label: {
                            VStack(spacing: 6) {
                                Image(systemName: "ellipsis")
                                    .font(.title2)
                                    .foregroundColor(.white)
                                Text("Más")
                                    .font(.caption)
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                        }
                    }
                }
                .padding(.bottom, 60)
                .padding(.trailing, 16)
            }
        }
    }
    

    
    private func initializeState() {
        self.likeCount = reel.reactionCount
        self.commentCount = reel.comments?.count ?? 0
        
        if let currentUser = SupabaseClient.shared.loadSession()?.user {
             self.isOwner = reel.creator_id.uuidString.lowercased() == currentUser.id.lowercased()
        }
        
        // Determine if liked by current user
        if let currentUser = SupabaseClient.shared.loadSession()?.user,
           let reactions = reel.post_reactions {
            self.isLiked = reactions.contains(where: { $0.user_id.uuidString.lowercased() == currentUser.id.lowercased() })
        }
        
        Task { await checkRelationship() }
    }
    
    private func checkRelationship() async {
        guard let currentId = SupabaseClient.shared.loadSession()?.user.id,
              reel.creator_id.uuidString.lowercased() != currentId.lowercased() else { return }
              
        do {
            let status = try await SupabaseClient.shared.checkRelationshipStatus(with: reel.creator_id)
            await MainActor.run { self.relationshipStatus = status }
        } catch {
            print("Error checking relationship in Reel: \(error)")
        }
    }
    
    private func followUser() {
        guard relationshipStatus == "none" else { return }
        
        // Optimistic
        relationshipStatus = "siguiendo" // Or 'pendiente' if private, but for now assuming public/follow logic
        
        Task {
            do {
                try await SupabaseClient.shared.followUser(targetId: reel.creator_id)
                // Re-check to confirm (e.g. if it became pending)
                await checkRelationship()
            } catch {
                print("Error following from Reel: \(error)")
                await MainActor.run { relationshipStatus = "none" }
            }
        }
    }
    
    private func toggleLike() {
        let originalState = isLiked
        let originalCount = likeCount
        
        // Optimistic Update
        isLiked.toggle()
        likeCount += isLiked ? 1 : -1
        appManager.playHaptic()
        
        Task {
            do {
                if isLiked {
                    try await SupabaseClient.shared.reactToPost(postId: reel.id.uuidString, emoji: "❤️")
                } else {
                    try await SupabaseClient.shared.removeReaction(postId: reel.id.uuidString)
                }
            } catch {
                print("❌ Error toggling like: \(error)")
                // Revert on error
                await MainActor.run {
                    self.isLiked = originalState
                    self.likeCount = originalCount
                }
            }
        }
    }
    
    private func deleteReel() {
        Task {
            do {
                try await SupabaseClient.shared.deletePost(id: reel.id)
                // Since this view doesn't own the list, we might need to trigger a refresh via NotificationCenter or callback
                // For now, post a notification
                NotificationCenter.default.post(name: NSNotification.Name("ReelDeleted"), object: nil)
            } catch {
                print("Error deleting reel: \(error)")
            }
        }
    }
}

struct EditReelSheet: View {
    let reel: PostDTO
    @Environment(\.dismiss) private var dismiss
    @State private var title: String = ""
    @State private var caption: String = ""
    @State private var category: String = "General"
    @State private var isLoading = false
    
    let categories = ["General", "Humor", "Educación", "Tecnología", "Arte", "Música"]
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Detalles del Reel")) {
                    TextField("Título", text: $title)
                    TextField("Descripción", text: $caption, axis: .vertical)
                        .lineLimit(3...6)
                    Picker("Categoría", selection: $category) {
                        ForEach(categories, id: \.self) { cat in
                            Text(cat).tag(cat)
                        }
                    }
                }
            }
            .navigationTitle("Editar Reel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") { saveChanges() }
                        .disabled(isLoading)
                }
            }
            .onAppear {
                title = reel.title ?? ""
                caption = reel.caption ?? ""
                category = reel.category ?? "General"
            }
        }
    }
    
    private func saveChanges() {
        isLoading = true
        Task {
            do {
                try await SupabaseClient.shared.updatePost(
                    id: reel.id,
                    title: title,
                    caption: caption,
                    category: category
                )
                // Use notification to refresh
                 NotificationCenter.default.post(name: NSNotification.Name("ReelUpdated"), object: nil)
                await MainActor.run { dismiss() }
            } catch {
                print("Error updating reel: \(error)")
            }
            isLoading = false
        }
    }
}

struct SidebarButton: View {
    let icon: String
    let text: String
    var color: Color = .white
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.title2)
                    .foregroundColor(color)
                Text(text)
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(.white)
            }
        }
    }
}

// MARK: - Video Player Logic (Robust)
struct CustomVideoPlayerView: UIViewRepresentable {
    let player: AVPlayer?
    
    func makeUIView(context: Context) -> VideoPlayerUIView {
        let view = VideoPlayerUIView()
        view.backgroundColor = .black
        return view
    }
    
    func updateUIView(_ uiView: VideoPlayerUIView, context: Context) {
        if uiView.playerLayer.player != player {
            uiView.playerLayer.player = player
        }
        // Force FIT to ensure full visibility without cropping
        if uiView.playerLayer.videoGravity != .resizeAspectFill {
            uiView.playerLayer.videoGravity = .resizeAspectFill // FILL: Standard immersive experience
        }
    }
}

class VideoPlayerUIView: UIView {
    // Defines the backing layer as AVPlayerLayer
    override class var layerClass: AnyClass {
        return AVPlayerLayer.self
    }
    
    var playerLayer: AVPlayerLayer {
        return layer as! AVPlayerLayer
    }
}

// MARK: - Video Manager
class VideoPlayerManager: NSObject, ObservableObject {
    @Published var player: AVPlayer?
    @Published var isPlaying: Bool = false
    private var playerLooper: AVPlayerLooper?
    private var playerItem: AVPlayerItem?
    
    func setupPlayer(url: URL, shouldLoop: Bool = true) {
        print("🎬 [Manager] Setup: \(url.absoluteString)")
        playerItem = AVPlayerItem(url: url)
        
        // Optimize buffering
        playerItem?.preferredForwardBufferDuration = 5.0
        
        if shouldLoop {
            let queuePlayer = AVQueuePlayer(playerItem: playerItem!)
            playerLooper = AVPlayerLooper(player: queuePlayer, templateItem: playerItem!)
            player = queuePlayer
        } else {
            player = AVPlayer(playerItem: playerItem!)
        }
        
        player?.isMuted = false
        player?.automaticallyWaitsToMinimizeStalling = true
        
        playerItem?.addObserver(self, forKeyPath: "status", options: [.new], context: nil)
    }
    
    override func observeValue(forKeyPath keyPath: String?, of object: Any?, change: [NSKeyValueChangeKey : Any]?, context: UnsafeMutableRawPointer?) {
        if keyPath == "status", let item = object as? AVPlayerItem {
            if item.status == .readyToPlay {
                print("✅ [Manager] Ready to play")
                // Auto resume if needed
            } else if item.status == .failed {
                print("❌ [Manager] Failed: \(item.error?.localizedDescription ?? "unknown")")
            }
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
    
    func setMuted(_ muted: Bool) {
        player?.isMuted = muted
    }
    
    deinit {
        playerItem?.removeObserver(self, forKeyPath: "status")
        player?.pause()
        playerLooper?.disableLooping()
        player = nil
        playerItem = nil
        playerLooper = nil
    }
}

// MARK: - Comments Sheet (Kept same)
struct CommentsSheet: View {
    let postId: String
    @Environment(\.dismiss) private var dismiss
    @State private var comments: [CommentDTO] = []
    @State private var newComment: String = ""
    @State private var isLoading: Bool = true
    @State private var editingComment: CommentDTO?
    @State private var showingDeleteAlert = false
    @State private var commentToDelete: CommentDTO?
    
    var body: some View {
        NavigationView {
            VStack(spacing: 0) {
                ScrollView {
                    if isLoading {
                        ProgressView().padding().padding(.top, 20)
                    } else if comments.isEmpty {
                        VStack(spacing: 10) {
                            Image(systemName: "bubble.left.and.bubble.right")
                                .font(.system(size: 40))
                                .foregroundColor(.gray.opacity(0.5))
                            Text("Sé el primero en comentar")
                                .foregroundColor(.secondary)
                        }
                        .padding(.top, 50)
                    } else {
                        LazyVStack(alignment: .leading, spacing: 16) {
                            ForEach(comments) { comment in
                                CommentRow(comment: comment, onEdit: {
                                    editingComment = comment
                                }, onDelete: {
                                    commentToDelete = comment
                                    showingDeleteAlert = true
                                })
                            }
                        }
                        .padding()
                    }
                }
                
                Divider()
                
                HStack {
                    TextField("Escribe un comentario...", text: $newComment)
                        .textFieldStyle(.roundedBorder)
                        .submitLabel(.send)
                        .onSubmit { postComment() }
                    
                    Button(action: postComment) {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(newComment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? .gray : LyrionTheme.primaryPurple)
                    }
                    .disabled(newComment.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding()
                .padding(.bottom, 10)
            }
            .navigationTitle("Comentarios")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") { dismiss() }
                }
            }
            .task { await loadComments() }
            .alert("¿Eliminar comentario?", isPresented: $showingDeleteAlert, presenting: commentToDelete) { comment in
                Button("Cancelar", role: .cancel) {}
                Button("Eliminar", role: .destructive) {
                    deleteComment(comment)
                }
            } message: { _ in
                 Text("¿Estás seguro?")
            }
            .sheet(item: $editingComment) { comment in
                EditCommentView(comment: comment) {
                    Task { await loadComments() }
                }
                .presentationDetents([.fraction(0.3)])
            }
        }
    }
    
    private func deleteComment(_ comment: CommentDTO) {
        Task {
            try? await SupabaseClient.shared.deleteComment(commentId: comment.id.uuidString)
            await loadComments()
        }
    }
    
    private func loadComments() async {
        isLoading = true
        do {
            comments = try await SupabaseClient.shared.fetchComments(for: postId)
        } catch {
            print("Error: \(error)")
        }
        isLoading = false
    }
    
    private func postComment() {
        let text = newComment.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        newComment = ""
        Task {
            try? await SupabaseClient.shared.addComment(postId: postId, content: text)
            await loadComments()
        }
    }
}

struct CommentRow: View {
    let comment: CommentDTO
    var onEdit: () -> Void
    var onDelete: () -> Void
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            AvatarView(url: comment.user?.avatar_url, name: comment.user?.username, size: 36)
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(comment.user?.username ?? "Usuario").font(.subheadline).bold()
                    Spacer()
                    Text(comment.created_at.formatted(.relative(presentation: .named))).font(.caption2).foregroundColor(.secondary)
                    
                    if isOwner {
                        Menu {
                            Button(action: onEdit) {
                                Label("Editar", systemImage: "pencil")
                            }
                            Button(role: .destructive, action: onDelete) {
                                Label("Eliminar", systemImage: "trash")
                            }
                        } label: {
                            Image(systemName: "ellipsis")
                                .font(.caption)
                                .foregroundColor(.gray)
                                .padding(4)
                        }
                    }
                }
                Text(comment.content).font(.subheadline)
            }
        }
    }
    
    private var isOwner: Bool {
        guard let currentId = SupabaseClient.shared.loadSession()?.user.id else { return false }
        return comment.user_id.uuidString.lowercased() == currentId.lowercased()
    }
}
