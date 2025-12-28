import SwiftData
import SwiftUI

struct SearchView: View {
    @State private var searchText = ""
    @Query var userProfiles: [UserProfile]
    @State private var showEditProfile = false
    
    // Supabase State
    @State private var searchResults: [ProfileDTO] = []
    @State private var isSearching = false

    @State private var errorMessage: String?
    
    // Debug Connection State
    @State private var connectionStatus: String = ""
    @State private var showConnectionAlert = false
    @State private var isTestingConnection = false
    
    var body: some View {
        NavigationStack {
            SocialFeedView()
                .navigationTitle("")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar(.hidden, for: .navigationBar) // Hide default nav bar to use ours
        }
    }
    
    // MARK: - Debug Connection
    private func testDatabaseConnection() {
        isTestingConnection = true
        connectionStatus = "Probando conexión..."
        
        Task {
            do {
                // 1. Probar Lectura (Fetch Profile)
                if let _ = try await SupabaseClient.shared.fetchCurrentUserProfile() {
                    connectionStatus = "✅ Lectura OK: Perfil encontrado.\n"
                } else {
                    connectionStatus = "⚠️ Lectura OK: No se encontró perfil (pero conectó).\n"
                }
                
                // 2. Probar Escritura (Update - dummy)
                // Usamos el perfil actual local para re-enviarlo
                if let profile = userProfiles.first, let user = SupabaseClient.shared.loadSession()?.user {
                    let userUUID = UUID(uuidString: user.id) ?? UUID()
                     let profileDTO = ProfileDTO(
                        id: userUUID,
                        username: user.email,
                        nombre: profile.name,
                        apellido: profile.lastName,
                        avatarUrl: profile.avatarUrl,
                        bannerUrl: nil,
                        bio: nil,
                        occupation: nil,
                        country: profile.country,
                        edad: profile.age,
                        altura: Int(profile.height),
                        peso: Int(profile.weight),
                        estadoCivil: profile.maritalStatus,
                        estadoRegion: profile.state,
                        isActive: true,
                        lastActiveAt: ISO8601DateFormatter().string(from: Date())
                    )
                    try await SupabaseClient.shared.updateUserProfile(id: user.id, profile: profileDTO)
                    connectionStatus += "✅ Escritura OK: Perfil actualizado."
                } else {
                     connectionStatus += "⚠️ No se pudo probar escritura (faltan datos locales o sesión)."
                }
                
            } catch {
                connectionStatus = "❌ Error de conexión:\n\(error.localizedDescription)"
            }
            
            await MainActor.run {
                showConnectionAlert = true
                isTestingConnection = false
            }
        }
    }
    
    private func performSearch() {
        guard !searchText.isEmpty else {
            // If cleared, reload available users
            loadAvailableUsers()
            return 
        }
        
        isSearching = true
        errorMessage = nil
        
        Task {
            do {
                let results = try await SupabaseClient.shared.searchProfiles(query: searchText)
                await MainActor.run {
                    self.searchResults = results
                    self.isSearching = false
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isSearching = false
                }
            }
        }
    }
    
    // Load initial users to populate view
    private func loadAvailableUsers() {
        // isLoading ...
        Task {
            do {
                let recentUsers = try await SupabaseClient.shared.fetchAvailableUsers()
                await MainActor.run {
                    self.searchResults = recentUsers
                }
            } catch {
                print("Failed to load initial users: \(error)")
            }
        }
    }
    
    // Extracted for cleanliness
    private func localProfileView(profile: UserProfile) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Mi Perfil")
                    .font(.headline)
                Spacer()
                Button("Editar") {
                    showEditProfile = true
                }
                .font(.subheadline)
                .foregroundColor(.blue)
            }
            .padding(.horizontal)
            
            VStack(alignment: .leading, spacing: 8) {
                Text("Nombre: \(profile.name) \(profile.lastName)")
                Text("Estatura: \(String(format: "%.1f", profile.height)) cm")
                Text("Ubicación: \(profile.state), \(profile.country)")
            }
            .padding()
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.gray.opacity(0.1))
            .cornerRadius(10)
            .padding(.horizontal)
        }
        .padding(.bottom, 20)
        .sheet(isPresented: $showEditProfile) {
            NavigationStack {
                EditProfileView(profile: profile)
            }
        }
    }
}

// MARK: - Subviews

struct UserResultRow: View {
    let user: ProfileDTO
    
    var body: some View {
        HStack(spacing: 12) {
            // Avatar Placeholder
            Circle()
                .fill(Color.blue.opacity(0.2))
                .frame(width: 50, height: 50)
                .overlay(Text(String(user.username?.prefix(1) ?? "U").uppercased()))
            
            VStack(alignment: .leading, spacing: 4) {
                if let nombre = user.nombre, !nombre.isEmpty {
                    Text("\(nombre) \(user.apellido ?? "")")
                        .font(.headline)
                        .foregroundColor(.primary)
                    
                    Text(user.username ?? "Usuario")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                } else {
                    Text(user.username ?? "Usuario")
                        .font(.headline)
                        .foregroundColor(.primary)
                }
            }
            
            Spacer()
            
            Image(systemName: "bubble.right.fill")
                .foregroundColor(.blue)
        }
        .padding(.horizontal)
        .padding(.vertical, 8)
        .background(Color.white.opacity(0.05))
        .cornerRadius(10)
        .padding(.horizontal)
    }
}

struct SearchBar: View {
    @Binding var text: String
    var onSearch: () -> Void = {}
    
    var body: some View {
        HStack {
            Image(systemName: "magnifyingglass")
                .foregroundColor(.secondary)
            
            TextField("Buscar usuarios...", text: $text)
                .textFieldStyle(PlainTextFieldStyle())
                .onSubmit {
                    onSearch()
                }
            
            if !text.isEmpty {
                Button(action: {
                    text = ""
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .foregroundColor(.secondary)
                }
            }
            
            Button("Buscar") {
                onSearch()
            }
            .font(.caption)
            .foregroundColor(.blue)
        }
        .padding(10)
        #if os(iOS)
        .background(Color(UIColor.tertiarySystemBackground))
        #else
        .background(Color.gray.opacity(0.1))
        #endif
        .cornerRadius(10)
    }
}

// MARK: - Direct Chat View
struct DirectChatView: View {
    let targetUser: ProfileDTO
    @Environment(\.modelContext) private var modelContext
    @State private var messages: [MessageDTO] = []
    @State private var localMessages: [LocalMessage] = []
    @State private var newMessageText = ""
    @State private var isLoading = true
    @State private var relationshipStatus: String = "none"
    @State private var replyingTo: MessageDTO? = nil
    @Environment(\.dismiss) private var dismiss
    
    var body: some View {
        ZStack {
            // Background Layer
            LiquidBackground()
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                // Custom Header
                GlassHeader(targetUser: targetUser, relationshipStatus: relationshipStatus, onDismiss: { dismiss() }, onFollow: followUser)
                
                // Messages List
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 20) {
                            if messages.isEmpty {
                                emptyStateView
                            } else {
                                dateHeader("Hoy")
                                
                                ForEach(messages) { msg in
                                    MessageBubble(message: msg, isFromMe: msg.senderId.uuidString.lowercased() != targetUser.id.uuidString.lowercased(), onReply: { message in
                                        withAnimation(.spring()) {
                                            replyingTo = message
                                        }
                                    })
                                    .id(msg.id)
                                }
                            }
                            
                            Color.clear.frame(height: 120) // Bottom Padding for input
                        }
                        .padding(.horizontal)
                    }
                    .onChange(of: messages.count) {
                        if let lastId = messages.last?.id {
                            withAnimation(.spring()) {
                                proxy.scrollTo(lastId, anchor: .bottom)
                            }
                        }
                    }
                    .onAppear {
                        if let lastId = messages.last?.id {
                            proxy.scrollTo(lastId, anchor: .bottom)
                        }
                    }
                }
            }
            
            // Floating Input Area
            VStack {
                Spacer()
                
                VStack(spacing: 0) {
                    // Reply Context UI
                    if let replyingTo = replyingTo {
                        replyContextView(replyingTo)
                    }
                    
                    inputFieldView
                }
                .background(.ultraThinMaterial)
                .cornerRadius(24)
                .overlay(
                    RoundedRectangle(cornerRadius: 24)
                        .stroke(Color.white.opacity(0.15), lineWidth: 1)
                )
                .padding(.horizontal, 16)
                .padding(.bottom, 110)
                .shadow(color: .black.opacity(0.4), radius: 20, x: 0, y: 10)
            }
        }
        .navigationBarHidden(true)
        .onAppear {
            loadMessages()
            checkStatus()
        }
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 16) {
            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 50))
                .foregroundColor(.white.opacity(0.2))
            Text("No hay mensajes aún")
                .font(.headline)
                .foregroundColor(.white.opacity(0.6))
            Text("¡Saluda a \(targetUser.username ?? "este usuario")!")
                .font(.subheadline)
                .foregroundColor(.white.opacity(0.4))
        }
        .padding(.top, 100)
    }
    
    private func dateHeader(_ text: String) -> some View {
        Text(text)
            .font(.caption.bold())
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
            .background(.ultraThinMaterial)
            .foregroundColor(.white.opacity(0.7))
            .cornerRadius(100)
            .overlay(
                RoundedRectangle(cornerRadius: 100)
                    .stroke(Color.white.opacity(0.1), lineWidth: 0.5)
            )
            .padding(.vertical, 20)
    }
    
    private func replyContextView(_ replyingTo: MessageDTO) -> some View {
        HStack {
            Rectangle()
                .fill(Color.white.opacity(0.6))
                .frame(width: 4)
                .cornerRadius(2)
            
            VStack(alignment: .leading, spacing: 2) {
                Text("Respondiendo a \(replyingTo.senderId.uuidString.lowercased() == targetUser.id.uuidString.lowercased() ? (targetUser.username ?? "usuario") : "ti mismo")")
                    .font(.caption.bold())
                    .foregroundColor(.blue.opacity(0.8))
                Text(replyingTo.content)
                    .font(.subheadline)
                    .foregroundColor(.white.opacity(0.8))
                    .lineLimit(1)
            }
            .padding(.vertical, 8)
            .padding(.horizontal, 8)
            
            Spacer()
            
            Button(action: {
                withAnimation { self.replyingTo = nil }
            }) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundColor(.white.opacity(0.3))
            }
            .padding(.trailing, 12)
        }
        .background(Color.black.opacity(0.3))
        .cornerRadius(12, corners: [.topLeft, .topRight])
    }
    
    private var inputFieldView: some View {
        HStack(spacing: 12) {
            Button(action: {}) {
                Image(systemName: "plus")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(.white.opacity(0.6))
            }
            
            TextField("Mensaje...", text: $newMessageText)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.white.opacity(0.08))
                .cornerRadius(12)
                .foregroundColor(.white)
            
            Button(action: sendMessage) {
                ZStack {
                    LinearGradient(colors: [.blue, .purple], startPoint: .topLeading, endPoint: .bottomTrailing)
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                        .offset(x: 1, y: -1)
                }
                .frame(width: 44, height: 44)
                .cornerRadius(22)
                .shadow(color: .blue.opacity(0.4), radius: 10, x: 0, y: 5)
            }
            .disabled(newMessageText.isEmpty)
            .opacity(newMessageText.isEmpty ? 0.6 : 1.0)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
    
    private func checkStatus() {
        Task {
            do {
                let status = try await SupabaseClient.shared.checkRelationshipStatus(with: targetUser.id)
                await MainActor.run { self.relationshipStatus = status }
            } catch {
                print("Error checking status: \(error)")
            }
        }
    }
    
    private func sendFriendRequest() {
        Task {
            try? await SupabaseClient.shared.sendFriendRequest(targetId: targetUser.id)
            await MainActor.run { relationshipStatus = "pendiente" }
        }
    }
    
    private func loadMessages() {
        Task {
            do {
                // 1. Cargar mensajes locales (SwiftData)
                let currentUserId = SupabaseClient.shared.loadSession()?.user.id ?? ""
                let targetIdStr = targetUser.id.uuidString.lowercased()
                let currentUserIdStr = currentUserId.lowercased()
                
                let descriptor = FetchDescriptor<LocalMessage>()
                let allLocal = try modelContext.fetch(descriptor)
                
                print("🔍 DEBUG: Loaded \(allLocal.count) total local messages")
                
                let conversationLocal = allLocal.filter { local in
                    let sId = local.senderId.uuidString.lowercased()
                    let rId = local.receiverId.uuidString.lowercased()
                    
                    return (sId == currentUserIdStr && rId == targetIdStr) ||
                           (sId == targetIdStr && rId == currentUserIdStr)
                }
                
                print("🔍 DEBUG: \(conversationLocal.count) local messages for this conversation")
                
                // 2. Cargar mensajes remotos (Supabase)
                let remoteMessages = try await SupabaseClient.shared.fetchMessages(with: targetUser.id.uuidString)
                
                // 3. Marcar remotos como leídos y guardarlos localmente ANTES de mostrarlos
                await markReceivedMessagesAsRead(remoteMessages)
                
                // 4. Combinar y mostrar (solo locales después de la sincronización)
                // Volvemos a leer locales porque markReceived acabade insertar nuevos
                let updatedLocal = try modelContext.fetch(descriptor).filter { local in
                    let sId = local.senderId.uuidString.lowercased()
                    let rId = local.receiverId.uuidString.lowercased()
                    return (sId == currentUserIdStr && rId == targetIdStr) ||
                           (sId == targetIdStr && rId == currentUserIdStr)
                }
                
                updatedLocal.forEach { msg in
                    print("Local message context check: \(msg.replyContextContent ?? "nil")")
                }
                
                let finalMessages = updatedLocal.map { mapLocalMessageToDTO($0) }
                
                await MainActor.run {
                    self.messages = finalMessages.sorted(by: { $0.createdAt < $1.createdAt })
                    self.isLoading = false
                }
            } catch {
                print("Error al cargar mensajes: \(error)")
                await MainActor.run { self.isLoading = false }
            }
        }
    }
    
    private func mapLocalMessageToDTO(_ local: LocalMessage) -> MessageDTO {
        let sender = PostCreator(username: local.senderUsername, nombre: nil, apellido: nil, avatar_url: local.senderAvatarUrl)
        
        var replyToMessage: BoxedMessageDTO? = nil
        if let replyId = local.replyToId, let replyContent = local.replyContextContent {
            let ghostSender = PostCreator(username: local.replyContextSenderUsername ?? "Usuario", nombre: nil, apellido: nil, avatar_url: nil)
            let ghostParent = MessageDTO(
                id: Int(replyId),
                senderId: UUID(), 
                receiverId: UUID(), 
                content: replyContent,
                isTemporary: true,
                seenAt: nil,
                createdAt: Date(),
                status: nil,
                replyToId: nil,
                replyContextContent: nil,
                replyContextSenderUsername: nil,
                sender: ghostSender,
                receiver: nil,
                replyToMessage: nil
            )
            replyToMessage = BoxedMessageDTO(message: ghostParent)
        }
        
        return MessageDTO(
            id: Int(local.id),
            senderId: local.senderId,
            receiverId: local.receiverId,
            content: local.content,
            isTemporary: true,
            seenAt: nil,
            createdAt: local.createdAt,
            status: "read",
            replyToId: local.replyToId != nil ? Int(local.replyToId!) : nil,
            replyContextContent: local.replyContextContent,
            replyContextSenderUsername: local.replyContextSenderUsername,
            sender: sender,
            receiver: nil,
            replyToMessage: replyToMessage
        )
    }
    
    private func markReceivedMessagesAsRead(_ msgs: [MessageDTO]) async {
        guard let currentId = SupabaseClient.shared.loadSession()?.user.id else { return }
        
        for msg in msgs {
            if msg.senderId.uuidString.lowercased() != currentId.lowercased() && msg.status != "read" {
                do {
                    try await SupabaseClient.shared.markMessageRead(messageId: Int64(msg.id))
                    
                    let localMessage = LocalMessage(
                        id: Int64(msg.id),
                        senderId: msg.senderId,
                        receiverId: msg.receiverId,
                        content: msg.content,
                        createdAt: msg.createdAt,
                        readAt: Date(),
                        senderUsername: msg.sender?.username,
                        senderAvatarUrl: msg.sender?.avatar_url,
                        replyToId: msg.replyToId != nil ? Int64(msg.replyToId!) : nil,
                        replyContextContent: msg.replyContextContent ?? msg.replyToMessage?.message.content,
                        replyContextSenderUsername: msg.replyContextSenderUsername ?? msg.replyToMessage?.message.sender?.username
                    )
                    
                    await MainActor.run {
                        modelContext.insert(localMessage)
                        do {
                            try modelContext.save()
                        } catch {
                            print("❌ SwiftData SAVE ERROR: \(error)")
                        }
                    }
                    
                    try await SupabaseClient.shared.deleteDirectMessage(messageId: Int64(msg.id))
                } catch {
                    print("❌ Error processing read message: \(error)")
                }
            }
        }
    }
    
    private func sendMessage() {
        guard !newMessageText.isEmpty else { return }
        let textToSend = newMessageText
        newMessageText = ""
        
        let capturedReplyingTo = replyingTo
        
        Task {
            do {
                let newMessage = try await SupabaseClient.shared.sendMessage(
                    to: targetUser.id.uuidString,
                    content: textToSend,
                    replyToId: capturedReplyingTo?.id,
                    replyContext: capturedReplyingTo?.content,
                    replyContextSender: capturedReplyingTo?.sender?.username ?? "Usuario"
                )
                
                await MainActor.run {
                    self.replyingTo = nil
                }
                
                if let currentUserId = UUID(uuidString: SupabaseClient.shared.loadSession()?.user.id ?? "") {
                    let localMessage = LocalMessage(
                        id: Int64(newMessage.id),
                        senderId: currentUserId,
                        receiverId: targetUser.id,
                        content: newMessage.content,
                        createdAt: newMessage.createdAt,
                        readAt: Date(),
                        senderUsername: nil,
                        senderAvatarUrl: nil,
                        replyToId: capturedReplyingTo != nil ? Int64(capturedReplyingTo!.id) : nil,
                        replyContextContent: capturedReplyingTo?.content,
                        replyContextSenderUsername: capturedReplyingTo?.sender?.username
                    )
                    await MainActor.run {
                        modelContext.insert(localMessage)
                        try? modelContext.save()
                    }
                }
                
                var messageToAppend = newMessage
                if let replyParent = capturedReplyingTo {
                    messageToAppend = MessageDTO(
                        id: newMessage.id,
                        senderId: newMessage.senderId,
                        receiverId: newMessage.receiverId,
                        content: newMessage.content,
                        isTemporary: newMessage.isTemporary,
                        seenAt: newMessage.seenAt,
                        createdAt: newMessage.createdAt,
                        status: newMessage.status,
                        replyToId: newMessage.replyToId,
                        replyContextContent: capturedReplyingTo?.content,
                        replyContextSenderUsername: capturedReplyingTo?.sender?.username,
                        sender: newMessage.sender,
                        receiver: newMessage.receiver,
                        replyToMessage: BoxedMessageDTO(message: replyParent)
                    )
                }
                
                await MainActor.run {
                    self.messages.append(messageToAppend)
                }
            } catch {
                print("❌ Error sending message: \(error)")
            }
        }
    }
    
    private func followUser() {
        Task {
            do {
                try await SupabaseClient.shared.followUser(targetId: targetUser.id)
                await MainActor.run { 
                    relationshipStatus = "siguiendo" 
                }
            } catch {
                print("Error following user: \(error)")
            }
        }
    }
}

struct MessageBubble: View {
    let message: MessageDTO
    let isFromMe: Bool
    var onReply: (MessageDTO) -> Void = { _ in }
    
    var body: some View {
        HStack {
            if isFromMe { Spacer() }
            
            VStack(alignment: isFromMe ? .trailing : .leading, spacing: 4) {
                
                // Reply Context Bubble (Inside Glass)
                if let repliedMsg = message.replyToMessage?.message {
                    HStack(spacing: 8) {
                        Rectangle()
                            .fill(isFromMe ? Color.white.opacity(0.4) : Color.blue.opacity(0.4))
                            .frame(width: 3)
                            .cornerRadius(1.5)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text(repliedMsg.sender?.username ?? "Usuario")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(isFromMe ? .yellow.opacity(0.8) : .orange.opacity(0.8))
                            Text(repliedMsg.content)
                                .font(.system(size: 12))
                                .foregroundColor(.white.opacity(0.9))
                                .lineLimit(1)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color.black.opacity(0.05))
                    .cornerRadius(8)
                    .padding(.bottom, -8)
                    .padding(.horizontal, 4)
                    .zIndex(1)
                }

                // Main Bubble
                Text(message.content)
                    .font(.system(size: 15))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(
                        Group {
                            if isFromMe {
                                LinearGradient(colors: [.blue.opacity(0.8), .purple.opacity(0.8)], startPoint: .topLeading, endPoint: .bottomTrailing)
                            } else {
                                Color.white.opacity(0.12)
                            }
                        }
                    )
                    .background(.ultraThinMaterial)
                    .foregroundColor(.white)
                    .clipShape(BubbleShape(isFromMe: isFromMe))
                    .overlay(
                        BubbleShape(isFromMe: isFromMe)
                            .stroke(Color.white.opacity(0.1), lineWidth: 0.5)
                    )
                    .shadow(color: .black.opacity(0.1), radius: 5, x: 0, y: 2)
                    .contextMenu {
                        Button { onReply(message) } label: {
                            Label("Responder", systemImage: "arrowshape.turn.up.left")
                        }
                    }
                
                // Timestamp & Status
                HStack(spacing: 4) {
                    Text(message.createdAt.formatted(.dateTime.hour().minute()))
                        .font(.system(size: 9))
                        .foregroundColor(.white.opacity(0.4))
                    
                    if isFromMe {
                        StatusIndicator(status: message.status)
                    }
                }
                .padding(.horizontal, 4)
            }
            
            if !isFromMe { Spacer() }
        }
    }
}

struct StatusIndicator: View {
    let status: String?
    
    var body: some View {
        HStack(spacing: -2) {
            if status == "read" {
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.blue)
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.blue)
            } else if status == "delivered" {
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.white.opacity(0.4))
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.white.opacity(0.4))
            } else {
                Image(systemName: "checkmark")
                    .font(.system(size: 8, weight: .bold))
                    .foregroundColor(.white.opacity(0.4))
            }
        }
    }
}

struct BubbleShape: Shape {
    let isFromMe: Bool
    
    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: [
                .topLeft, .topRight,
                isFromMe ? .bottomLeft : .bottomRight
            ],
            cornerRadii: CGSize(width: 16, height: 16)
        )
        return Path(path.cgPath)
    }
}

// MARK: - Helper Design Views

struct LiquidBackground: View {
    @State private var animate = false
    
    var body: some View {
        ZStack {
            Color(red: 0.02, green: 0.02, blue: 0.02).ignoresSafeArea()
            
            // Liquid Gradients
            Group {
                RadialGradient(colors: [Color.blue.opacity(0.15), .clear], center: animate ? .topLeading : .center, startRadius: 100, endRadius: 600)
                RadialGradient(colors: [Color.purple.opacity(0.1), .clear], center: animate ? .bottomTrailing : .topTrailing, startRadius: 100, endRadius: 700)
                RadialGradient(colors: [Color.pink.opacity(0.05), .clear], center: animate ? .bottomLeading : .bottomTrailing, startRadius: 100, endRadius: 500)
            }
            .blur(radius: 60)
            .animation(.easeInOut(duration: 10).repeatForever(autoreverses: true), value: animate)
        }
        .onAppear { animate = true }
    }
}

struct GlassHeader: View {
    let targetUser: ProfileDTO
    let relationshipStatus: String
    var onDismiss: () -> Void
    var onFollow: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            Color.clear.frame(height: 10)
            
            HStack(spacing: 16) {
                Button(action: onDismiss) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 18, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 36, height: 36)
                        .background(.white.opacity(0.1))
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.white.opacity(0.15), lineWidth: 1))
                }
                
                VStack(alignment: .center, spacing: 2) {
                    Text((targetUser.nombre != nil && !targetUser.nombre!.isEmpty) ? "\(targetUser.nombre!) \(targetUser.apellido ?? "")" : (targetUser.username ?? "Chat"))
                        .font(.headline.bold())
                        .foregroundColor(.white)
                    
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color.green)
                            .frame(width: 6, height: 6)
                        Text("En línea")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.green.opacity(0.8))
                    }
                }
                .frame(maxWidth: .infinity)
                
                // Avatar / Profile Button
                Circle()
                    .fill(LinearGradient(colors: [.blue.opacity(0.6), .purple.opacity(0.6)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 36, height: 36)
                    .overlay(
                        Text(String(targetUser.username?.prefix(1) ?? "U").uppercased())
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                    )
                    .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
            }
            .padding(.horizontal)
            .padding(.bottom, 12)
        }
        .background(.ultraThinMaterial)
        .overlay(Rectangle().fill(Color.white.opacity(0.1)).frame(height: 0.5), alignment: .bottom)
    }
}

// MARK: - Extensions
// redundant declarations removed (already in SocialFeedView.swift)
