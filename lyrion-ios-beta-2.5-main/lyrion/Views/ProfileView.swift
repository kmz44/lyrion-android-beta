//
//  ProfileView.swift
//  Lyrion
//
//
//

import SwiftUI
import SwiftData
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

struct ProfileView: View {
    @EnvironmentObject var appManager: AppManager
    @EnvironmentObject var authManager: AuthManager
    @Environment(\.modelContext) private var modelContext
    @Query private var userProfiles: [UserProfile]
    @Query(sort: \UserAttribute.order) private var userAttributes: [UserAttribute]
    
    @Binding var showSettings: Bool
    @AppStorage("selectedLanguage") private var selectedLanguage = "es"
    @State private var showSignOutConfirmation = false
    @State private var showDeleteAccountConfirmation = false
    
    // Banner states
    @State private var showBannerPicker = false
    @State private var selectedBannerImage: UIImage?
    @State private var isUploadingBanner = false
    @State private var currentBannerUrl: String?
    
    // Stats states
    @State private var postsCount: Int = 0
    @State private var followersCount: Int = 0
    @State private var followingCount: Int = 0
    @State private var friendsCount: Int = 0
    
    // Navigation states
    @State private var showUserData = false
    @State private var showUserAttributes = false
    @State private var showHomeStyle = false
    @State private var showVoiceSettings = false
    @State private var showHomeAISettings = false
    
    // Stories states
    @State private var showStoryUpload = false
    @State private var showStoryViewer = false
    @State private var myStories: [SupabaseClient.StoryDTO] = []
    
    var hasActiveStories: Bool {
        !myStories.isEmpty
    }

    private var systemBackground: Color {
        #if os(iOS)
        return Color(UIColor.systemBackground)
        #elseif os(macOS)
        return Color(NSColor.windowBackgroundColor)
        #endif
    }
    
    var body: some View {
        ZStack {
            // Main Background
            systemBackground.ignoresSafeArea() // Custom color or system
            
            VStack(spacing: 0) {
                // Header (Simple)
                HStack {
                    Text("Perfil")
                        .font(.system(size: 24, weight: .bold))
                    Spacer()
                    Button {
                        // Menu action (opcional o settings)
                        showSettings = true 
                    } label: {
                        Image(systemName: "ellipsis")
                            .font(.system(size: 20))
                            .foregroundColor(.primary)
                            .padding(8)
                            .background(Color.gray.opacity(0.1))
                            .clipShape(Circle())
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
                .background(systemBackground)
                
                ScrollView(showsIndicators: false) {
                    VStack(spacing: 0) {
                        // Banner Section
                        bannerSection
                        
                        // Profile Info Section (Avatar, Name, Stats)
                        VStack(spacing: 0) {
                            // Avatar (Offset negativo para subir sobre el banner)
                            avatarSection
                                .offset(y: -64) // Mitad de 128px
                                .padding(.bottom, -64 + 12) // Compensar espacio
                            
                            // User Info
                            userInfoSection
                            
                            // Stats
                            statsSection
                                .padding(.top, 24)
                        }
                        .padding(.horizontal, 20)
                        
                        // Menu Options List
                        menuOptionsSection
                            .padding(.top, 32)
                            .padding(.horizontal, 16)
                        
                        // Footer Buttons (Logout, Delete)
                        footerButtonsSection
                            .padding(.top, 24)
                            .padding(.horizontal, 16)
                            .padding(.bottom, 40)
                    }
                }
            }
        }
        .task {
            await loadBannerUrl()
            await loadStats()
            await loadMyStories()
        }
        .onAppear {
            Task {
                await loadBannerUrl()
                await loadStats()
                await loadMyStories()
            }
        }
        .onChange(of: authManager.currentUser) { _, _ in
            Task {
                await loadBannerUrl()
                await loadStats()
                await loadMyStories()
            }
        }
        .fullScreenCover(isPresented: $showStoryViewer) {
            StoryViewer(stories: myStories)
        }
        .sheet(isPresented: $showStoryUpload) {
            StoryUploadView {
                // On uploaded
                Task { await loadMyStories() }
            }
        }
        .sheet(isPresented: $showUserData) {
            if let profile = userProfiles.first {
                NavigationStack { UserDataView(profile: profile) }
            }
        }
        .sheet(isPresented: $showUserAttributes) {
            NavigationStack { UserAttributesView() }
        }
        .sheet(isPresented: $showHomeStyle) {
            if let profile = userProfiles.first {
                NavigationStack { HomeStyleView(profile: profile) }
            }
        }
        .sheet(isPresented: $showVoiceSettings) {
             NavigationStack { VoiceSettingsView() }
        }
        .sheet(isPresented: $showHomeAISettings) {
            HomeAISettingsView()
        }
        .sheet(isPresented: $showSettings) {
             NavigationStack { SettingsView(currentThread: .constant(nil)) }
        }
        .sheet(isPresented: $showBannerPicker) {
            ImagePicker(image: $selectedBannerImage)
        }
        .onChange(of: selectedBannerImage) { _, newImage in
            if let image = newImage {
                uploadBanner(image)
            }
        }
        .alert("Cerrar Sesión", isPresented: $showSignOutConfirmation) {
            Button("Cancelar", role: .cancel) {}
            Button("Cerrar Sesión", role: .destructive) { authManager.signOut() }
        } message: {
            Text("¿Estás seguro de que quieres cerrar sesión?")
        }
        .alert("Borrar Cuenta", isPresented: $showDeleteAccountConfirmation) {
            Button("Cancelar", role: .cancel) {}
            Button("Borrar", role: .destructive) { 
                // Implement delete account logic here
                print("Delete account")
            }
        } message: {
            Text("Esta acción es irreversible. ¿Deseas continuar?")
        }
    }
    
    // MARK: - Sections
    
    private var bannerSection: some View {
        ZStack(alignment: .bottomTrailing) {
            // Banner Image
            if let bannerUrl = currentBannerUrl, let url = URL(string: bannerUrl) {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(height: 200)
                            .clipped()
                    default:
                        bannerPlaceholder
                    }
                }
                .frame(height: 200)
            } else {
                bannerPlaceholder
            }
            
            // Gradient Overlay
            LinearGradient(
                colors: [Color.black.opacity(0.6), .clear],
                startPoint: .bottom,
                endPoint: .center
            )
            .frame(height: 100)
            
            // Edit Buttons (Bottom Right)
            HStack(spacing: 12) {
                if currentBannerUrl != nil {
                    Button {
                        deleteBanner()
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .frame(width: 40, height: 40)
                            .background(Color.red)
                            .clipShape(Circle())
                            .shadow(radius: 4)
                    }
                }
                
                Button {
                    showBannerPicker = true
                } label: {
                    Image(systemName: "camera.fill")
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                        .frame(width: 40, height: 40)
                        .background(Color.black.opacity(0.6))
                        .clipShape(Circle())
                        .overlay(Circle().stroke(Color.white.opacity(0.2), lineWidth: 1))
                        .backdropEffect()
                }
            }
            .padding(16)
            
            if isUploadingBanner {
                ProgressView()
                    .tint(.white)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.black.opacity(0.4))
            }
        }
        .frame(height: 200)
    }
    
    private var bannerPlaceholder: some View {
        Color.gray.opacity(0.2) // Placeholder color
            .overlay(
                Image(systemName: "photo")
                    .font(.largeTitle)
                    .foregroundColor(.gray)
            )
            .frame(height: 200)
    }
    
    private var avatarSection: some View {
        ZStack(alignment: .bottomTrailing) {
            // Avatar Circle
            Button {
                if hasActiveStories {
                    showStoryViewer = true
                }
            } label: {
                Group {
                    if let profile = userProfiles.first, let localData = profile.localAvatarData, let uiImage = UIImage(data: localData) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: 128, height: 128)
                            .clipShape(Circle())
                    } else if let profile = userProfiles.first, let avatarUrl = profile.avatarUrl {
                         AvatarView(url: avatarUrl, name: profile.name, size: 128, hasStories: hasActiveStories)
                    } else if let user = authManager.currentUser {
                         AvatarView(url: user.userMetadata?.avatarUrl, name: user.userMetadata?.fullName, size: 128, hasStories: hasActiveStories)
                    } else {
                        Color.orange 
                           .frame(width: 128, height: 128)
                           .clipShape(Circle())
                           .overlay(Text("?").font(.title).foregroundColor(.white))
                    }
                }
                // Border for background blending
                .overlay(
                   Circle().stroke(systemBackground, lineWidth: hasActiveStories ? 4 : 6)
                )
                .shadow(color: Color.black.opacity(0.1), radius: 10, x: 0, y: 5)
            }
            .disabled(!hasActiveStories) // Disable tap if no stories (upload button handles that)
            // Note: We might want tap on avatar to ALWAYS allow upload if no stories?
            // For now, let's keep upload separate button for clarity.

            // "+" Button for Upload
            Button {
                showStoryUpload = true
            } label: {
                ZStack {
                    Circle()
                        .fill(Color.blue)
                        .frame(width: 32, height: 32)
                        .overlay(Circle().stroke(systemBackground, lineWidth: 3))
                    
                    Image(systemName: "plus")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }
            }
            .offset(x: 0, y: 0) // Position at bottom right of avatar

        }
    }
    
    private var userInfoSection: some View {
        VStack(spacing: 4) {
            Text(userProfiles.first?.name ?? authManager.currentUser?.userMetadata?.fullName ?? "Usuario")
                .font(.system(size: 24, weight: .bold))
                .foregroundColor(.primary)
            
            if let profile = userProfiles.first {
                Text("\(profile.age) años • \(profile.country)")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.secondary)
            } else {
                 Text("Información no disponible")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(.secondary)
            }
        }
    }
    
    private var statsSection: some View {
        HStack(spacing: 0) {
            ProfileStatItem(count: postsCount, label: "Posts")
            Divider().frame(height: 24)
            ProfileStatItem(count: followersCount, label: "Seguidores")
            Divider().frame(height: 24)
            ProfileStatItem(count: followingCount, label: "Seguidos")
            Divider().frame(height: 24)
            ProfileStatItem(count: friendsCount, label: "Amigos")
        }
        .frame(maxWidth: .infinity)
    }
    
    private var menuOptionsSection: some View {
        VStack(spacing: 0) {
            Group {
                MenuOptionRow(icon: "person.text.rectangle", iconColor: .purple, bgColor: .purple.opacity(0.1), title: "Datos del Usuario") {
                    showUserData = true
                }
                Divider().padding(.leading, 60)
                
                MenuOptionRow(icon: "chart.bar.fill", iconColor: .indigo, bgColor: .indigo.opacity(0.1), title: "Atributos") {
                    showUserAttributes = true
                }
                Divider().padding(.leading, 60)
                
                MenuOptionRow(icon: "waveform", iconColor: .blue, bgColor: .blue.opacity(0.1), title: "Ajustes de Voz") {
                    showVoiceSettings = true
                }
                Divider().padding(.leading, 60)
                
                MenuOptionRow(icon: "brain", iconColor: .pink, bgColor: .pink.opacity(0.1), title: "IA de Inicio") {
                    showHomeAISettings = true
                }
                Divider().padding(.leading, 60)
                
                MenuOptionRow(icon: "paintpalette.fill", iconColor: .orange, bgColor: .orange.opacity(0.1), title: "Personalizar Inicio") {
                    showHomeStyle = true
                }
                Divider().padding(.leading, 60)
                
                MenuOptionRow(icon: "lock.shield", iconColor: .teal, bgColor: .teal.opacity(0.1), title: "Privacidad") {
                    // Action privacy
                }
            }
        }
        .background(Color(UIColor.secondarySystemGroupedBackground))
        .cornerRadius(16)
        .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
        .padding(1) // Border-like trick if needed or use overlay stroke
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.gray.opacity(0.1), lineWidth: 1)
        )
    }
    
    private var footerButtonsSection: some View {
        HStack(spacing: 16) {
            // Logout
            Button {
                showSignOutConfirmation = true
            } label: {
                VStack(spacing: 8) {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                        .font(.system(size: 24))
                        .foregroundColor(.purple)
                    Text("Cerrar Sesión")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.primary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Color(UIColor.secondarySystemGroupedBackground))
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.gray.opacity(0.1), lineWidth: 1)
                )
            }
            .buttonStyle(ScaleButtonStyle())
            
            // Delete Account
            Button {
                showDeleteAccountConfirmation = true
            } label: {
                VStack(spacing: 8) {
                    Image(systemName: "trash")
                        .font(.system(size: 24))
                        .foregroundColor(.red)
                    Text("Borrar Cuenta")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(.red)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(Color(UIColor.secondarySystemGroupedBackground))
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color.gray.opacity(0.1), lineWidth: 1)
                )
            }
            .buttonStyle(ScaleButtonStyle())
        }
    }
    
    // MARK: - Logic
    
    private func loadBannerUrl() async {
        do {
            if let profile = try await SupabaseClient.shared.fetchCurrentUserProfile() {
                await MainActor.run {
                    currentBannerUrl = profile.bannerUrl
                }
            }
        } catch {
            print("Error loading banner: \(error)")
        }
    }
    
    private func loadStats() async {
         guard let userIdString = authManager.currentUser?.id,
               let userId = UUID(uuidString: userIdString) else { return }
         do {
             let stats = try await SupabaseClient.shared.countUserStats(userId: userId)
             await MainActor.run {
                 self.postsCount = stats.posts
                 self.followersCount = stats.followers
                 self.followingCount = stats.following
                 self.friendsCount = stats.friends
             }
         } catch {
             print("Error loading stats: \(error)")
         }
    }
    
    private func loadMyStories() async {
        do {
            let stories = try await SupabaseClient.shared.fetchMyActiveStories()
            await MainActor.run {
                self.myStories = stories
            }
        } catch {
            print("Error loading stories: \(error)")
        }
    }
    
    private func uploadBanner(_ image: UIImage) {
        Task {
            isUploadingBanner = true
            do {
                let url = try await SupabaseClient.shared.uploadBanner(image)
                await MainActor.run {
                    currentBannerUrl = url
                    selectedBannerImage = nil
                    isUploadingBanner = false
                }
            } catch {
                print("Error uploading banner: \(error)")
                await MainActor.run { isUploadingBanner = false }
            }
        }
    }
    
    private func deleteBanner() {
        Task {
            isUploadingBanner = true
            do {
                try await SupabaseClient.shared.deleteBanner()
                await MainActor.run {
                    currentBannerUrl = nil
                    isUploadingBanner = false
                }
            } catch {
                print("Error deleting banner: \(error)")
                await MainActor.run { isUploadingBanner = false }
            }
        }
    }
}

// MARK: - Subviews & Styles

struct ProfileStatItem: View {
    let count: Int
    let label: String
    
    var body: some View {
        VStack(spacing: 2) {
            Text("\(count)")
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.primary)
            Text(label.uppercased())
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.secondary)
                .tracking(1) // uppercase tracking-wide
        }
        .frame(maxWidth: .infinity)
    }
}

struct MenuOptionRow: View {
    let icon: String
    let iconColor: Color
    let bgColor: Color
    let title: String
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 16) {
                // Icon Box
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(bgColor)
                        .frame(width: 36, height: 36)
                    
                    Image(systemName: icon)
                        .font(.system(size: 18))
                        .foregroundColor(iconColor)
                }
                
                Text(title)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundColor(.primary)
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 16))
                    .foregroundColor(.gray.opacity(0.6))
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 16)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}



extension View {
    func backdropEffect() -> some View {
        #if os(iOS)
        self.background(Material.ultraThin)
        #else
        self.background(Color.black.opacity(0.5))
        #endif
    }
}

struct ImagePicker: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    @Environment(\.dismiss) private var dismiss
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .photoLibrary
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: ImagePicker
        
        init(_ parent: ImagePicker) {
            self.parent = parent
        }
        
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.image = image
            }
            parent.dismiss()
        }
        
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}


