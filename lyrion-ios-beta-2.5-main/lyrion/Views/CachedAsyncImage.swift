import SwiftUI

struct CachedAsyncImage<Content: View, Placeholder: View>: View {
    let url: URL?
    let content: (Image) -> Content
    let placeholder: () -> Placeholder
    
    @State private var image: UIImage? = nil
    @State private var isLoading = false
    
    init(
        url: URL?,
        @ViewBuilder content: @escaping (Image) -> Content,
        @ViewBuilder placeholder: @escaping () -> Placeholder
    ) {
        self.url = url
        self.content = content
        self.placeholder = placeholder
    }
    
    var body: some View {
        ZStack {
            if let uiImage = image {
                content(Image(uiImage: uiImage))
            } else {
                placeholder()
                    .onAppear {
                        loadImage()
                    }
            }
        }
    }
    
    private func loadImage() {
        guard let url = url, !isLoading else { return }
        
        // 1. Check Cache Immediately (Main Thread Check for speed, but Data access should be careful)
        // Standard URLCache is thread safe.
        let request = URLRequest(url: url)
        if let cached = URLCache.shared.cachedResponse(for: request),
           let cachedImage = UIImage(data: cached.data) {
            self.image = cachedImage
            return
        }
        
        // 2. Network Fetch
        isLoading = true
        Task {
            do {
                let (data, response) = try await URLSession.shared.data(for: request)
                // Cache is handled automatically by URLSession configuration if headers allow, 
                // but we can also force save it if implicit caching fails due to Strict headers.
                // Given we control the cache in App, standard behavior implies we respect headers. 
                // But typically Supabase headers are fine.
                // The prefetch definitely put it in cache.
                
                if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200,
                   let newImage = UIImage(data: data) {
                    await MainActor.run {
                        self.image = newImage
                        self.isLoading = false
                    }
                }
            } catch {
                print("Error loading image \(url): \(error)")
                await MainActor.run { isLoading = false }
            }
        }
    }
}
