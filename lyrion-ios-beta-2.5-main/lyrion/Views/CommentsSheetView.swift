//
//  CommentsSheetView.swift
//  Lyrion
//
//  Created for Lyrion IA
//

import SwiftUI
import UIKit

// MARK: - Tree Model
struct CommentNode: Identifiable {
    let id: UUID
    let comment: CommentDTO
    var children: [CommentNode]
}

struct CommentsSheetView: View {
    @State var postId: String
    var postCreatorId: String?
    
    @State private var commentNodes: [CommentNode] = [] // Tree roots
    @State private var flatComments: [CommentDTO] = [] // Raw data
    @State private var newCommentText = ""
    @State private var replyToComment: CommentDTO?
    @Environment(\.dismiss) var dismiss
    
    enum SortOption {
        case top, newest
    }
    @State private var sortOption: SortOption = .newest
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Custom Header
                headerView
                
                // Content
                ScrollView {
                    VStack(spacing: 0) {
                        filterBar
                        
                        if commentNodes.isEmpty && flatComments.isEmpty {
                            emptyStateView
                        } else {
                            LazyVStack(spacing: 24) { // Spacing between threads
                                ForEach(commentNodes) { node in
                                    CommentThreadView(
                                        node: node,
                                        postCreatorId: postCreatorId,
                                        onReply: { target in
                                            replyToComment = target
                                        },
                                        onRefresh: {
                                            Task { await loadComments() }
                                        }
                                    )
                                }
                            }
                            .padding(.horizontal)
                            .padding(.bottom, 100)
                        }
                    }
                    .padding(.top)
                }
                .background(Color(UIColor.systemBackground))
                
                // Input Area
                inputBar
            }
            .background(Color(UIColor.systemBackground))
            .navigationBarHidden(true)
            .task {
                await loadComments()
            }
        }
    }
    
    // MARK: - Logic
    
    private func loadComments() async {
        do {
            let fetched = try await SupabaseClient.shared.fetchComments(for: postId)
            await MainActor.run {
                self.flatComments = fetched
                self.commentNodes = buildTree(from: fetched)
            }
        } catch {
            print("Error loading comments: \(error)")
        }
    }
    
    // Convert flat list to tree
    private func buildTree(from comments: [CommentDTO]) -> [CommentNode] {
        var nodeMap = [UUID: CommentNode]()
        var roots = [CommentNode]()
        
        // 1. Create all nodes
        for c in comments {
            nodeMap[c.id] = CommentNode(id: c.id, comment: c, children: [])
        }
        
        // 2. Link them
        for c in comments {
            if let parentId = c.parent_id, var parentNode = nodeMap[parentId], let childNode = nodeMap[c.id] {
                // Append child to parent
                parentNode.children.append(childNode)
                // Update map (since struct is value type, need to re-insert)
                // Wait, structs in map: updating one doesn't update others reference if we copy.
                // Approach: Use simple 2-pass with class or careful struct handling.
                // Swift Struct approach:
                // Actually, easier to filter roots and children recursively or mutably.
            }
        }
        
        // Correct approach for value types:
        // Group by parentID
        let grouped = Dictionary(grouping: comments, by: { $0.parent_id })
        
        // Recursive helper
        func buildNode(for c: CommentDTO) -> CommentNode {
            let myChildren = grouped[c.id] ?? []
            let sortedChildren = myChildren.sorted { $0.created_at < $1.created_at } // Chronological
            return CommentNode(
                id: c.id,
                comment: c,
                children: sortedChildren.map { buildNode(for: $0) }
            )
        }
        
        // Roots are those with parent_id == nil
        let rootComments = grouped[nil] ?? []
        let sortedRoots = rootComments.sorted {
            if sortOption == .top {
                return ($0.likes_count ?? 0) > ($1.likes_count ?? 0)
            } else {
                return $0.created_at > $1.created_at // Newest first for roots usually
            }
        }
        
        return sortedRoots.map { buildNode(for: $0) }
    }
    
    private func postComment() {
        guard !newCommentText.isEmpty else { return }
        let text = newCommentText
        let parentId = replyToComment?.id
        newCommentText = ""
        replyToComment = nil
        
        Task {
            do {
                try await SupabaseClient.shared.postComment(postId: UUID(uuidString: postId)!, content: text, parentId: parentId)
                await loadComments()
            } catch {
                print("Error posting comment: \(error)")
            }
        }
    }
    
    // MARK: - Subviews
    private var headerView: some View {
        HStack {
            Button(action: { dismiss() }) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundColor(.primary)
                    .padding(8)
                    .background(Color.gray.opacity(0.1))
                    .clipShape(Circle())
            }
            Text("Comments").font(.headline).fontWeight(.bold)
            Spacer()
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
        .overlay(Rectangle().frame(height: 1).foregroundColor(Color.gray.opacity(0.1)), alignment: .bottom)
    }
    
    private var filterBar: some View {
        HStack {
            Text("Top comments").font(.subheadline).fontWeight(.semibold)
            Spacer()
        }
        .padding(.horizontal)
        .padding(.bottom, 12)
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 20) {
            Spacer().frame(height: 100)
            Image(systemName: "bubble.left.and.bubble.right").font(.system(size: 50)).foregroundColor(.gray.opacity(0.3))
            Text("No comments yet").font(.headline).foregroundColor(.secondary)
        }
    }
    
    private var inputBar: some View {
        VStack(spacing: 0) {
            Divider()
            
            if let replyTo = replyToComment {
                HStack {
                    Rectangle().fill(Color.gray.opacity(0.3)).frame(width: 2).padding(.vertical, 4)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Replying to").font(.caption2).foregroundColor(.secondary)
                        Text(replyTo.user?.username ?? "User").font(.caption).fontWeight(.medium)
                    }
                    Spacer()
                    Button(action: { withAnimation { replyToComment = nil } }) {
                        Image(systemName: "xmark").foregroundColor(.secondary)
                    }
                }
                .padding()
                .background(Color(UIColor.secondarySystemBackground))
            }
            
            HStack(alignment: .bottom, spacing: 8) {
                if let currentUser = SupabaseClient.shared.loadSession()?.user {
                     AvatarView(url: nil, name: "Me", size: 36)
                }
                
                HStack {
                    TextField("Add a comment...", text: $newCommentText, axis: .vertical)
                        .font(.body)
                        .padding(10)
                }
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(20)
                
                Button(action: postComment) {
                     Image(systemName: "arrow.up")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundColor(.white)
                        .frame(width: 40, height: 40)
                        .background(LyrionTheme.primaryPurple)
                        .clipShape(Circle())
                }
                .disabled(newCommentText.isEmpty)
            }
            .padding()
        }
    }
}

// MARK: - Recursive Thread View
struct CommentThreadView: View {
    let node: CommentNode
    let postCreatorId: String?
    let onReply: (CommentDTO) -> Void
    var onRefresh: (() -> Void)?
    
    // State for collapsing
    @State private var isExpanded: Bool = false
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Root comment of this branch
            CommentRowContent(
                comment: node.comment,
                postCreatorId: postCreatorId,
                onReply: { onReply(node.comment) },
                onRefresh: onRefresh
            )
            .contentShape(Rectangle()) // Make touch target better
            
            // Interaction for expanding/collapsing if children exist
            if !node.children.isEmpty {
                VStack(alignment: .leading, spacing: 0) {
                    if isExpanded {
                        // Expanded Children
                        HStack(alignment: .top, spacing: 0) {
                            // Thread Guide Line
                            Color.clear
                                .frame(width: 18)
                                .overlay(
                                    Rectangle()
                                        .fill(Color.gray.opacity(0.2))
                                        .frame(width: 2)
                                        .padding(.top, -12) // Connect to parent
                                        .padding(.bottom, 0)
                                    , alignment: .trailing
                                )
                                .padding(.trailing, 12) // Spacing to content
                            
                            VStack(alignment: .leading, spacing: 16) {
                                ForEach(node.children) { childNode in
                                    CommentThreadView(node: childNode, postCreatorId: postCreatorId, onReply: onReply, onRefresh: onRefresh)
                                        .overlay(
                                            // Curve/Elbow for each child
                                            Path { path in
                                                path.move(to: CGPoint(x: -30, y: 18)) // Connect from main line
                                                path.addQuadCurve(to: CGPoint(x: 0, y: 18), control: CGPoint(x: -15, y: 18))
                                            }
                                            .stroke(Color.gray.opacity(0.2), lineWidth: 2)
                                            , alignment: .topLeading
                                        )
                                }
                            }
                            .frame(maxWidth: .infinity) // Ensure children take full available width
                        }
                        .padding(.top, 12)
                        
                        // Collapse Button (Optional, usually we just toggle via the "Hide" text or toggle on the "View replies" button itself. 
                        // But standard UI is "View replies" -> turns into hiding or simply shows them.
                    }
                    
                    // Toggle Button
                    Button(action: { 
                        withAnimation { isExpanded.toggle() }
                    }) {
                        HStack(spacing: 8) {
                            Rectangle()
                                .fill(Color.gray.opacity(0.2))
                                .frame(width: 30, height: 1) // Horizontal dash
                            
                            Text(isExpanded ? "Hide replies" : "View \(node.children.count) replies")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(.secondary)
                        }
                        .padding(.vertical, 8)
                        .padding(.leading, 18) // Align with text start roughly
                    }
                }
            }
        }
    }
}

// Simplified Row for re-use
struct CommentRowContent: View {
    let comment: CommentDTO
    let postCreatorId: String?
    let onReply: () -> Void
    var onRefresh: (() -> Void)?
    @State private var likes: Int
    @State private var dislikes: Int
    @State private var myReaction: String? = nil
    @State private var showEditSheet = false
    @State private var showDeleteAlert = false
    
    init(comment: CommentDTO, postCreatorId: String?, onReply: @escaping () -> Void, onRefresh: (() -> Void)? = nil) {
        self.comment = comment
        self.postCreatorId = postCreatorId
        self.onReply = onReply
        self.onRefresh = onRefresh
        _likes = State(initialValue: comment.likes_count ?? 0)
        _dislikes = State(initialValue: comment.dislikes_count ?? 0)
    }
    
    private var isOwner: Bool {
        guard let currentId = SupabaseClient.shared.loadSession()?.user.id else { return false }
        return comment.user_id.uuidString.lowercased() == currentId.lowercased()
    }
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            AvatarView(url: comment.user?.avatar_url, name: comment.user?.username, size: 36) // Fixed size for consistency
            
            VStack(alignment: .leading, spacing: 4) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(displayName)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.primary)
                        
                        Text(comment.created_at.formatted(.relative(presentation: .named)))
                            .font(.caption)
                            .foregroundColor(.secondary)
                        
                        Spacer()
                        
                        // Edit/Delete Menu for owner
                        if isOwner {
                            Menu {
                                Button(action: { showEditSheet = true }) {
                                    Label("Editar", systemImage: "pencil")
                                }
                                Button(role: .destructive, action: { showDeleteAlert = true }) {
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
                    
                    Text(comment.content)
                        .font(.system(size: 14))
                        .foregroundColor(.primary)
                        .fixedSize(horizontal: false, vertical: true) // Allow multiline growth
                }
                .padding(12)
                .background(Color(UIColor.secondarySystemBackground))
                .cornerRadius(16, corners: [.topRight, .bottomLeft, .bottomRight])
                .cornerRadius(4, corners: [.topLeft])
                
                HStack(spacing: 16) {
                    Button(action: onReply) {
                        Text("Reply")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.secondary)
                    }
                    
                    // Like Button - tap to toggle, long press for menu
                    Button(action: {
                        // Simple tap toggles the reaction
                        if myReaction == "like" {
                            likes -= 1
                            myReaction = nil
                            Task { try? await SupabaseClient.shared.removeCommentReaction(commentId: comment.id) }
                        } else {
                            if myReaction == "dislike" { dislikes -= 1 }
                            likes += 1
                            myReaction = "like"
                            Task { try? await SupabaseClient.shared.reactToComment(commentId: comment.id, type: "like") }
                        }
                    }) {
                        HStack(spacing: 2) {
                            Image(systemName: myReaction == "like" ? "heart.fill" : "heart")
                            if likes > 0 { Text("\(likes)") }
                        }
                        .font(.caption)
                        .foregroundColor(myReaction == "like" ? .red : .secondary)
                    }
                    .contextMenu {
                        Button(action: {
                            if myReaction != "like" {
                                if myReaction == "dislike" { dislikes -= 1 }
                                likes += 1
                                myReaction = "like"
                                Task { try? await SupabaseClient.shared.reactToComment(commentId: comment.id, type: "like") }
                            }
                        }) {
                            Label("Me gusta", systemImage: "heart.fill")
                        }
                        
                        Button(role: .destructive, action: {
                            if myReaction == "like" { likes -= 1 }
                            else if myReaction == "dislike" { dislikes -= 1 }
                            myReaction = nil
                            Task { try? await SupabaseClient.shared.removeCommentReaction(commentId: comment.id) }
                        }) {
                            Label("Dejar de reaccionar", systemImage: "xmark.circle")
                        }
                    }
                    
                    // Dislike Button - tap to toggle, long press for menu
                    Button(action: {
                        // Simple tap toggles the reaction
                        if myReaction == "dislike" {
                            dislikes -= 1
                            myReaction = nil
                            Task { try? await SupabaseClient.shared.removeCommentReaction(commentId: comment.id) }
                        } else {
                            if myReaction == "like" { likes -= 1 }
                            dislikes += 1
                            myReaction = "dislike"
                            Task { try? await SupabaseClient.shared.reactToComment(commentId: comment.id, type: "dislike") }
                        }
                    }) {
                        HStack(spacing: 2) {
                            Image(systemName: myReaction == "dislike" ? "hand.thumbsdown.fill" : "hand.thumbsdown")
                            if dislikes > 0 { Text("\(dislikes)") }
                        }
                        .font(.caption)
                        .foregroundColor(myReaction == "dislike" ? .blue : .secondary)
                    }
                    .contextMenu {
                        Button(action: {
                            if myReaction != "dislike" {
                                if myReaction == "like" { likes -= 1 }
                                dislikes += 1
                                myReaction = "dislike"
                                Task { try? await SupabaseClient.shared.reactToComment(commentId: comment.id, type: "dislike") }
                            }
                        }) {
                            Label("No me gusta", systemImage: "hand.thumbsdown.fill")
                        }
                        
                        Button(role: .destructive, action: {
                            if myReaction == "like" { likes -= 1 }
                            else if myReaction == "dislike" { dislikes -= 1 }
                            myReaction = nil
                            Task { try? await SupabaseClient.shared.removeCommentReaction(commentId: comment.id) }
                        }) {
                            Label("Dejar de reaccionar", systemImage: "xmark.circle")
                        }
                    }
                }
                .padding(.leading, 4)
            }
        }
        .alert("¿Eliminar comentario?", isPresented: $showDeleteAlert) {
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) {
                Task {
                    try? await SupabaseClient.shared.deleteComment(commentId: comment.id.uuidString)
                    onRefresh?()
                }
            }
        } message: {
            Text("Esta acción no se puede deshacer.")
        }
        .sheet(isPresented: $showEditSheet) {
            EditCommentView(comment: comment) {
                onRefresh?()
            }
            .presentationDetents([.fraction(0.3)])
        }
        .task {
            // Load user's existing reaction on appear
            if let reaction = try? await SupabaseClient.shared.fetchMyCommentReaction(commentId: comment.id) {
                await MainActor.run {
                    myReaction = reaction
                }
            }
        }
    }
    
    var displayName: String {
        if let nombre = comment.user?.nombre, !nombre.isEmpty { return "\(nombre) \(comment.user?.apellido ?? "")" }
        return comment.user?.username ?? "User"
    }
}


