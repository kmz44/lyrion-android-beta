//
//  CreatePostView.swift
//  Lyrion
//
//  Created by Lyrion Team on 12/23/24.
//

import SwiftUI
import PhotosUI
import AVFoundation

struct CreatePostView: View {
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var appManager: AppManager
    
    // Media Selection
    @State private var mediaType: MediaType = .photo
    @State private var caption: String = ""
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var selectedImage: UIImage?
    @State private var selectedVideoURL: URL?
    @State private var videoThumbnail: UIImage?
    
    // Options
    @State private var isAnonymous = false
    @State private var contentType: ContentType = .post
    @State private var title: String = ""
    @State private var category: String = "General"
    
    let categories = ["General", "Tecnología", "Humor", "Educación", "Deportes", "Música", "Arte", "Estilo de vida", "Noticias"]
    
    // State
    @State private var isPosting = false
    @State private var errorMessage: String?
    @State private var showVideoPicker = false
    
    enum MediaType: String, CaseIterable {
        case photo = "Foto"
        case video = "Video"
        
        var icon: String {
            switch self {
            case .photo: return "photo"
            case .video: return "video"
            }
        }
    }
    
    enum ContentType: String, CaseIterable {
        case post = "Solo Posts"
        case reel = "Solo Reels"
        case both = "Posts y Reels"
        
        var value: String {
            switch self {
            case .post: return "post"
            case .reel: return "reel"
            case .both: return "both"
            }
        }
    }
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    // Media Type Selector
                    Picker("Tipo de contenido", selection: $mediaType) {
                        ForEach(MediaType.allCases, id: \.self) { type in
                            Label(type.rawValue, systemImage: type.icon)
                                .tag(type)
                        }
                    }
                    .pickerStyle(.segmented)
                    .padding(.horizontal)
                    .onChange(of: mediaType) { oldValue, newValue in
                        // Reset selection when changing media type
                        selectedImage = nil
                        selectedVideoURL = nil
                        selectedPhotoItem = nil
                        videoThumbnail = nil
                    }
                    
                    // Media Selection Area
                    if mediaType == .photo {
                        photoSelectionView
                    } else {
                        videoSelectionView
                    }
                    
                    // Title and Category
                    VStack(spacing: 12) {
                        TextField("Título", text: $title)
                            .textFieldStyle(RoundedBorderTextFieldStyle())
                            .font(.headline)
                        
                        HStack {
                            Text("Categoría")
                            Spacer()
                            Picker("Categoría", selection: $category) {
                                ForEach(categories, id: \.self) { cat in
                                    Text(cat).tag(cat)
                                }
                            }
                        }
                        
                        Text("La categoría ayuda a recomendar tu contenido al público adecuado.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal)
                    
                    // Caption
                    TextField("Escribe una descripción...", text: $caption, axis: .vertical)
                        .lineLimit(3...6)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                        .padding(.horizontal)
                    
                    // Options
                    VStack(alignment: .leading, spacing: 16) {
                        // Anonymous Toggle
                        Toggle(isOn: $isAnonymous) {
                            HStack {
                                Image(systemName: "eye.slash.fill")
                                    .foregroundColor(.secondary)
                                Text("Publicar como Anónimo")
                                    .font(.subheadline)
                            }
                        }
                        
                        // Content Type Selector (only for videos)
                        if mediaType == .video {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("Dónde aparecerá:")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                
                                Picker("Ubicación", selection: $contentType) {
                                    ForEach(ContentType.allCases, id: \.self) { type in
                                        Text(type.rawValue).tag(type)
                                    }
                                }
                                .pickerStyle(.segmented)
                            }
                        }
                    }
                    .padding(.horizontal)
                    
                    if let error = errorMessage {
                        Text(error)
                            .foregroundColor(.red)
                            .font(.caption)
                            .padding(.horizontal)
                    }
                }
                .padding(.vertical)
            }
            .navigationTitle(mediaType == .photo ? "Nuevo Post" : "Nuevo Reel")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: createPost) {
                        if isPosting {
                            ProgressView()
                        } else {
                            Text("Publicar")
                                .fontWeight(.bold)
                        }
                    }
                    .disabled(!canPost || isPosting)
                }
            }
            .onChange(of: selectedPhotoItem) { oldValue, newValue in
                Task {
                    if let data = try? await newValue?.loadTransferable(type: Data.self),
                       let uiImage = UIImage(data: data) {
                        await MainActor.run {
                            self.selectedImage = uiImage
                        }
                    }
                }
            }
            .sheet(isPresented: $showVideoPicker) {
                VideoPickerView { url in
                    selectedVideoURL = url
                    // Generate thumbnail
                    if let thumbnail = generateThumbnail(from: url) {
                        videoThumbnail = thumbnail
                    }
                }
            }
        }
    }
    
    // MARK: - Photo Selection View
    private var photoSelectionView: some View {
        Group {
            if let image = selectedImage {
                ZStack(alignment: .topTrailing) {
                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .frame(maxHeight: 300)
                        .cornerRadius(12)
                    
                    Button(action: {
                        withAnimation {
                            selectedImage = nil
                            selectedPhotoItem = nil
                        }
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundColor(.white)
                            .shadow(radius: 2)
                    }
                    .padding(8)
                }
                .padding(.horizontal)
            } else {
                PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color(UIColor.secondarySystemBackground))
                            .frame(height: 200)
                        
                        VStack(spacing: 10) {
                            Image(systemName: "photo.badge.plus")
                                .font(.system(size: 40))
                                .foregroundColor(LyrionTheme.primaryPurple)
                            Text("Añadir foto")
                                .font(.headline)
                                .foregroundColor(.primary)
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
    
    // MARK: - Video Selection View
    private var videoSelectionView: some View {
        Group {
            if let videoURL = selectedVideoURL {
                ZStack(alignment: .topTrailing) {
                    VStack(spacing: 12) {
                        // Thumbnail or video preview
                        if let thumbnail = videoThumbnail {
                            Image(uiImage: thumbnail)
                                .resizable()
                                .aspectRatio(contentMode: .fit)
                                .frame(maxHeight: 300)
                                .cornerRadius(12)
                                .overlay(
                                    Image(systemName: "play.circle.fill")
                                        .font(.system(size: 50))
                                        .foregroundColor(.white)
                                        .shadow(radius: 4)
                                )
                        }
                        
                        // Video info
                        HStack {
                            Image(systemName: "video.fill")
                                .foregroundColor(LyrionTheme.primaryPurple)
                            Text(videoURL.lastPathComponent)
                                .font(.caption)
                                .lineLimit(1)
                            Spacer()
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(UIColor.secondarySystemBackground))
                        .cornerRadius(8)
                    }
                    
                    Button(action: {
                        withAnimation {
                            selectedVideoURL = nil
                            videoThumbnail = nil
                        }
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundColor(.white)
                            .shadow(radius: 2)
                    }
                    .padding(8)
                }
                .padding(.horizontal)
            } else {
                Button(action: {
                    showVideoPicker = true
                }) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color(UIColor.secondarySystemBackground))
                            .frame(height: 200)
                        
                        VStack(spacing: 10) {
                            Image(systemName: "video.badge.plus")
                                .font(.system(size: 40))
                                .foregroundColor(LyrionTheme.primaryPurple)
                            Text("Añadir video")
                                .font(.headline)
                                .foregroundColor(.primary)
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
    
    // MARK: - Helpers
    private var canPost: Bool {
        if mediaType == .photo {
            return selectedImage != nil
        } else {
            return selectedVideoURL != nil
        }
    }
    
    private func createPost() {
        isPosting = true
        errorMessage = nil
        
        Task {
            do {
                if mediaType == .photo {
                    guard let image = selectedImage else { return }
                    // For photos, always use 'post' as content_type
                    try await SupabaseClient.shared.createPost(
                        caption: caption,
                        image: image,
                        isAnonymous: isAnonymous,
                        contentType: "post",
                        title: title,
                        category: category
                    )
                } else {
                    guard let videoURL = selectedVideoURL else { return }
                    let duration = try await getVideoDuration(from: videoURL)
                    
                    try await SupabaseClient.shared.createVideoPost(
                        caption: caption,
                        videoURL: videoURL,
                        thumbnail: videoThumbnail,
                        isAnonymous: isAnonymous,
                        contentType: contentType.value,
                        durationSeconds: duration,
                        title: title,
                        category: category
                    )
                }
                
                await MainActor.run {
                    isPosting = false
                    appManager.playHaptic()
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    isPosting = false
                    errorMessage = "Error al publicar: \(error.localizedDescription)"
                }
            }
        }
    }
    
    private func generateThumbnail(from url: URL) -> UIImage? {
        let asset = AVAsset(url: url)
        let imageGenerator = AVAssetImageGenerator(asset: asset)
        imageGenerator.appliesPreferredTrackTransform = true
        
        let time = CMTime(seconds: 1, preferredTimescale: 60)
        
        do {
            let cgImage = try imageGenerator.copyCGImage(at: time, actualTime: nil)
            return UIImage(cgImage: cgImage)
        } catch {
            print("Error generating thumbnail: \(error)")
            return nil
        }
    }
    
    private func getVideoDuration(from url: URL) async throws -> Int {
        let asset = AVAsset(url: url)
        let duration = try await asset.load(.duration)
        return Int(CMTimeGetSeconds(duration))
    }
}

// MARK: - Video Picker View
struct VideoPickerView: UIViewControllerRepresentable {
    var onVideoPicked: (URL) -> Void
    @Environment(\.dismiss) private var dismiss
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .photoLibrary
        picker.mediaTypes = ["public.movie"]
        picker.videoQuality = .typeHigh
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: VideoPickerView
        
        init(_ parent: VideoPickerView) {
            self.parent = parent
        }
        
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let videoURL = info[.mediaURL] as? URL {
                parent.onVideoPicked(videoURL)
            }
            parent.dismiss()
        }
        
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
