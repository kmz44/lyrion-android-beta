//
//  PostDetailView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/23/24.
//

import SwiftUI

struct PostDetailView: View {
    let post: PostDTO
    @EnvironmentObject var appManager: AppManager
    @State private var comments: [CommentDTO] = []
    @State private var newCommentText = ""
    @State private var isLoadingComments = false
    @State private var isSending = false
    @State private var showingEditPost = false
    @State private var editingComment: CommentDTO?
    @State private var showingDeleteAlert = false
    @State private var itemToDelete: DeleteItem?
    
    enum DeleteItem {
        case post
        case comment(CommentDTO)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Post Content (Scrollable if needed, but usually header)
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    // Post Header
                    HStack {
                        AvatarView(url: post.creator?.avatar_url, name: post.creator?.username, size: 40)
                        Text(post.creator?.username ?? "Usuario")
                            .font(.headline)
                        Spacer()
                        
                        // Menu for Post Owner
                        if isPostOwner {
                            Menu {
                                Button(action: { showingEditPost = true }) {
                                    Label("Editar", systemImage: "pencil")
                                }
                                Button(role: .destructive, action: {
                                    itemToDelete = .post
                                    showingDeleteAlert = true
                                }) {
                                    Label("Eliminar", systemImage: "trash")
                                }
                            } label: {
                                Image(systemName: "ellipsis")
                                    .foregroundColor(.gray)
                                    .padding(8)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .sheet(isPresented: $showingEditPost) {
                         EditPostView(post: post) {
                             // Refresh logic?? We might need to pass a binding or reload. 
                             // Ideally parent view reloads, but for now we can't easily trigger parent reload.
                             // Maybe we just dismiss and user sees updated if they pull to refresh?
                             // But let's try to at least close it.
                         }
                    }
                    
                    // Image
                    AsyncImage(url: URL(string: post.media_url)) { image in
                        image.resizable().aspectRatio(contentMode: .fit)
                    } placeholder: {
                        Rectangle().fill(Color.gray.opacity(0.3)).aspectRatio(1, contentMode: .fit)
                    }
                    .frame(maxWidth: .infinity)
                    
                    // Caption
                    if let caption = post.caption, !caption.isEmpty {
                        Text(caption)
                            .padding(.horizontal)
                    }
                    
                    Divider()
                    
                    // Comments Header
                    Text("Comentarios")
                        .font(.headline)
                        .padding(.horizontal)
                        .padding(.top, 8)
                    
                    if isLoadingComments {
                        ProgressView().padding()
                    } else if comments.isEmpty {
                        Text("Se el primero en comentar")
                            .foregroundColor(.gray)
                            .padding()
                    } else {
                        ForEach(comments) { comment in
                            HStack(alignment: .top, spacing: 12) {
                                AvatarView(url: comment.user?.avatar_url, name: comment.user?.username, size: 32)
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack {
                                        Text(comment.user?.username ?? "Usuario")
                                            .font(.subheadline)
                                            .fontWeight(.semibold)
                                        Text(comment.created_at.formatted(.relative(presentation: .named)))
                                            .font(.caption2)
                                            .foregroundColor(.gray)
                                        
                                        Spacer()
                                        
                                        // Menu for Comment Owner
                                        if isCommentOwner(comment) {
                                            Menu {
                                                Button(action: { editingComment = comment }) {
                                                    Label("Editar", systemImage: "pencil")
                                                }
                                                Button(role: .destructive, action: {
                                                    itemToDelete = .comment(comment)
                                                    showingDeleteAlert = true
                                                }) {
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
                                        .font(.subheadline)
                                }
                                Spacer()
                            }
                            .padding(.horizontal)
                            .padding(.vertical, 4)
                        }
                    }
                }
                .padding(.vertical)
            }
            
            // Input Area
            HStack {
                TextField("Añadir un comentario...", text: $newCommentText)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .submitLabel(.send)
                    .onSubmit {
                        submitComment()
                    }
                
                Button(action: submitComment) {
                    if isSending {
                        ProgressView()
                    } else {
                        Image(systemName: "paperplane.fill")
                            .foregroundColor(LyrionTheme.primaryPurple)
                    }
                }
                .disabled(newCommentText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isSending)
            }
            .padding()
            .background(Color(UIColor.systemBackground))
            .shadow(radius: 2)
        }
        .task {
            await loadComments()
        }
        .alert("¿Confirmar eliminación?", isPresented: $showingDeleteAlert, presenting: itemToDelete) { item in
            Button("Cancelar", role: .cancel) {}
            Button("Eliminar", role: .destructive) {
                switch item {
                case .post:
                    deletePost()
                case .comment(let comment):
                    deleteComment(comment)
                }
            }
        } message: { item in
            switch item {
            case .post:
                Text("¿Estás seguro de que quieres eliminar esta publicación? No podrás deshacer esta acción.")
            case .comment:
                Text("¿Estás seguro de que quieres eliminar este comentario?")
            }
        }
        .sheet(item: $editingComment) { comment in
            EditCommentView(comment: comment) {
                Task { await loadComments() }
            }
            .presentationDetents([.fraction(0.3)])
        }
    }
    
    // MARK: - Owner Checks
    private var isPostOwner: Bool {
        guard let currentUserId = SupabaseClient.shared.loadSession()?.user.id else { return false }
        return post.creator_id.uuidString.lowercased() == currentUserId.lowercased()
    }
    
    private func isCommentOwner(_ comment: CommentDTO) -> Bool {
        guard let currentUserId = SupabaseClient.shared.loadSession()?.user.id else { return false }
        return comment.user_id.uuidString.lowercased() == currentUserId.lowercased()
    }
    
    // MARK: - Actions
    private func deletePost() {
        Task {
            do {
                try await SupabaseClient.shared.deletePost(postId: post.id.uuidString)
                // Dismiss View?
                 // Needs environment dismiss or navigation back
                 // Not injected here yet but usually useful
            } catch {
                print("Error removing post: \(error)")
            }
        }
    }
    
    private func deleteComment(_ comment: CommentDTO) {
        Task {
            do {
                try await SupabaseClient.shared.deleteComment(commentId: comment.id.uuidString)
                await loadComments()
            } catch {
                 print("Error removing comment: \(error)")
            }
        }
    }
    
    private func loadComments() async {
        isLoadingComments = true
        do {
            let fetched = try await SupabaseClient.shared.fetchComments(for: post.id.uuidString)
            await MainActor.run {
                self.comments = fetched
                self.isLoadingComments = false
            }
        } catch {
            print("Error loading comments: \(error)")
            await MainActor.run { isLoadingComments = false }
        }
    }
    
    private func submitComment() {
        let text = newCommentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        
        isSending = true
        Task {
            do {
                try await SupabaseClient.shared.addComment(postId: post.id.uuidString, content: text)
                await loadComments() // Reload to show new one
                await MainActor.run {
                    newCommentText = ""
                    isSending = false
                }
            } catch {
                print("Error sending comment: \(error)")
                await MainActor.run { isSending = false }
            }
        }
    }
}

// Simple internal view for comment editing
struct EditCommentView: View {
    let comment: CommentDTO
    var onUpdate: () -> Void
    @State private var text: String
    @Environment(\.dismiss) var dismiss
    
    init(comment: CommentDTO, onUpdate: @escaping () -> Void) {
        self.comment = comment
        _text = State(initialValue: comment.content)
        self.onUpdate = onUpdate
    }
    
    var body: some View {
        VStack {
            Text("Editar comentario")
                .font(.headline)
                .padding(.top)
            
            TextField("Comentario", text: $text)
                .textFieldStyle(.roundedBorder)
                .padding()
            
            HStack {
                Button("Cancelar") { dismiss() }
                Spacer()
                Button("Guardar") {
                    Task {
                        try? await SupabaseClient.shared.updateComment(commentId: comment.id.uuidString, content: text)
                        await MainActor.run {
                            onUpdate()
                            dismiss()
                        }
                    }
                }
                .disabled(text.isEmpty)
            }
            .padding()
        }
    }
}
