
import SwiftUI

struct StoryViewer: View {
    let stories: [SupabaseClient.StoryDTO]
    @Environment(\.dismiss) private var dismiss
    @State private var currentIndex = 0
    @State private var progress: CGFloat = 0.0
    @State private var timer: Timer?
    @State private var isPaused = false
    
    let durationPerStory: TimeInterval = 5.0
    
    var currentStory: SupabaseClient.StoryDTO {
        if stories.indices.contains(currentIndex) {
            return stories[currentIndex]
        }
        return stories[0]
    }
    
    var body: some View {
        Group {
            if stories.isEmpty {
                Color.black.ignoresSafeArea()
                    .onAppear {
                        dismiss()
                    }
            } else {
                ZStack {
                    Color.black.ignoresSafeArea()
                    
                    // Story Content
                    AsyncImage(url: URL(string: currentStory.mediaUrl)) { image in
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                    } placeholder: {
                        ProgressView()
                            .tint(.white)
                    }
                    .ignoresSafeArea()
                    .onTapGesture { location in
                        let screenWidth = UIScreen.main.bounds.width
                        if location.x < screenWidth / 2 {
                            previousStory()
                        } else {
                            nextStory()
                        }
                    }
                    .simultaneousGesture(
                        LongPressGesture(minimumDuration: 0.2)
                            .onEnded { _ in isPaused = true }
                            // Complex gesture handling for pause might need drag gesture state
                    )
                    
                    VStack {
                        // Progress Bars
                        HStack(spacing: 4) {
                            ForEach(0..<stories.count, id: \.self) { index in
                                GeometryReader { geometry in
                                    ZStack(alignment: .leading) {
                                        Capsule()
                                            .fill(Color.white.opacity(0.3))
                                        
                                        if index < currentIndex {
                                            Capsule().fill(Color.white)
                                        } else if index == currentIndex {
                                            Capsule()
                                                .fill(Color.white)
                                                .frame(width: geometry.size.width * progress)
                                        }
                                    }
                                }
                                .frame(height: 2)
                            }
                        }
                        .padding(.top, 40)
                        .padding(.horizontal)
                        
                        // Header (User Info)
                        HStack {
                            HStack {
                                if let avatarUrl = currentStory.avatarUrl {
                                   AvatarView(url: avatarUrl, name: currentStory.username, size: 32)
                                } else {
                                    Image(systemName: "person.circle.fill")
                                        .foregroundColor(.white)
                                        .font(.system(size: 32))
                                }
                                
                                VStack(alignment: .leading, spacing: 0) {
                                    if let nombre = currentStory.nombre, !nombre.isEmpty {
                                         Text("\(nombre) \(currentStory.apellido ?? "")")
                                            .font(.headline)
                                            .foregroundColor(.white)
                                    } else {
                                        Text(currentStory.username ?? "Usuario")
                                            .font(.headline)
                                            .foregroundColor(.white)
                                    }
                                }
                            }
                            
                            Spacer()
                            
                            Button {
                                dismiss()
                            } label: {
                                Image(systemName: "xmark")
                                    .foregroundColor(.white)
                                    .padding(8)
                            }
                        }
                        .padding(.horizontal)
                        .padding(.top, 8)
                        
                        Spacer()
                        
                        // Caption
                        if let caption = currentStory.caption {
                            Text(caption)
                                .foregroundColor(.white)
                                .padding()
                                .background(Color.black.opacity(0.3))
                                .cornerRadius(8)
                                .padding(.bottom, 40)
                        }
                    }
                }
                .onAppear {
                    startTimer()
                    markAsViewed(story: currentStory)
                }
                .onDisappear {
                    stopTimer()
                }
                .onChange(of: currentIndex) { _, _ in
                    progress = 0
                    markAsViewed(story: currentStory)
                }
            }
        }
    }
    
    private func startTimer() {
        stopTimer()
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { _ in
            if !isPaused {
                if progress >= 1.0 {
                    nextStory()
                } else {
                    progress += 0.05 / durationPerStory
                }
            }
        }
    }
    
    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }
    
    private func nextStory() {
        if currentIndex < stories.count - 1 {
            currentIndex += 1
            progress = 0
        } else {
            dismiss()
        }
    }
    
    private func previousStory() {
        if currentIndex > 0 {
            currentIndex -= 1
            progress = 0
        } else {
            progress = 0
        }
    }
    
    private func markAsViewed(story: SupabaseClient.StoryDTO) {
        Task {
            await SupabaseClient.shared.markStoryAsViewed(storyId: story.id)
        }
    }
}
