
import SwiftUI

struct StoryUploadView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var image: UIImage?
    @State private var caption: String = ""
    @State private var isUploading = false
    @State private var showImagePicker = false
    @State private var sourceType: UIImagePickerController.SourceType = .photoLibrary
    @State private var visibility: String = "followers" // Default visibility
    
    var onStoryUploaded: (() -> Void)?
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color.black.ignoresSafeArea()
                
                VStack {
                    if let image = image {
                        Image(uiImage: image)
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .cornerRadius(12)
                            .padding()
                        
                        TextField("Escribe un mensaje...", text: $caption)
                            .foregroundColor(.white)
                            .padding()
                            .background(Color.white.opacity(0.2))
                            .cornerRadius(8)
                            .padding(.horizontal)
                        
                        // Visibility Picker
                        HStack {
                            Text("Quién puede ver:")
                                .foregroundColor(.white)
                                .font(.subheadline)
                            
                            Spacer()
                            
                            Menu {
                                Button {
                                    visibility = "followers"
                                } label: {
                                    Label("Seguidores y Amigos", systemImage: "person.2")
                                }
                                
                                Button {
                                    visibility = "friends"
                                } label: {
                                    Label("Mejores Amigos", systemImage: "star.fill")
                                }
                            } label: {
                                HStack {
                                    Image(systemName: visibility == "followers" ? "person.2" : "star.fill")
                                    Text(visibility == "followers" ? "Seguidores y Amigos" : "Mejores Amigos")
                                    Image(systemName: "chevron.down")
                                        .font(.caption)
                                }
                                .foregroundColor(.white)
                                .padding(.vertical, 8)
                                .padding(.horizontal, 12)
                                .background(Color.white.opacity(0.1))
                                .cornerRadius(8)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 10)
                        
                        Button {
                            uploadStory()
                        } label: {
                            if isUploading {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("Compartir en mi Historia")
                                    .font(.headline)
                                    .foregroundColor(.white)
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .background(visibility == "friends" ? Color.green : Color.blue) // Green for friends
                                    .cornerRadius(12)
                            }
                        }
                        .disabled(isUploading)
                        .padding()
                        
                    } else {
                        VStack(spacing: 20) {
                            Button {
                                sourceType = .camera
                                showImagePicker = true
                            } label: {
                                Label("Tomar Foto", systemImage: "camera.fill")
                                    .font(.title2)
                                    .foregroundColor(.white)
                                    .padding()
                                    .background(Color.white.opacity(0.1))
                                    .clipShape(Capsule())
                            }
                            
                            Button {
                                sourceType = .photoLibrary
                                showImagePicker = true
                            } label: {
                                Label("Elegir de Galería", systemImage: "photo.on.rectangle")
                                    .font(.title2)
                                    .foregroundColor(.white)
                                    .padding()
                                    .background(Color.white.opacity(0.1))
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
            }
            .navigationTitle("Nueva Historia")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") { dismiss() }
                        .foregroundColor(.white)
                }
            }
            .sheet(isPresented: $showImagePicker) {
                ImagePicker2(image: $image, sourceType: sourceType)
            }
        }
    }
    
    private func uploadStory() {
        guard let image = image else { return }
        isUploading = true
        
        Task {
            do {
                _ = try await SupabaseClient.shared.uploadStory(image: image, caption: caption.isEmpty ? nil : caption, visibility: visibility)
                await MainActor.run {
                    isUploading = false
                    onStoryUploaded?()
                    dismiss()
                }
            } catch {
                print("Error uploading story: \(error)")
                await MainActor.run {
                    isUploading = false
                }
            }
        }
    }
}

struct ImagePicker2: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    var sourceType: UIImagePickerController.SourceType
    @Environment(\.dismiss) private var dismiss
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = sourceType
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: ImagePicker2
        
        init(_ parent: ImagePicker2) {
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
