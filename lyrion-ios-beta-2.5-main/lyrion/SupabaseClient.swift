//
//  SupabaseClient.swift
//  Lyrion
//

import Foundation
import UIKit

class SupabaseClient {
    static let shared = SupabaseClient()
    
    private let supabaseURL = "https://tcjhnibhoplfslqzprgl.supabase.co"
    private let supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRjamhuaWJob3BsZnNscXpwcmdsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTYzNjk1MTMsImV4cCI6MjA3MTk0NTUxM30.tEGffrsw9ed0ez7bLek1P8vrF8U7w5xEnOfyMgGOdqA"
    private let redirectURL = "lyrion://auth-callback"
    
    private init() {}
    
    // MARK: - Google OAuth
    func signInWithGoogle() async throws -> URL {
        let authURL = "\(supabaseURL)/auth/v1/authorize"
        
        // Scopes de Google Calendar para acceso completo
        let scopes = [
            "https://www.googleapis.com/auth/calendar",
            "https://www.googleapis.com/auth/calendar.events",
            "https://www.googleapis.com/auth/tasks"
        ].joined(separator: " ")
        
        var components = URLComponents(string: authURL)
        components?.queryItems = [
            URLQueryItem(name: "provider", value: "google"),
            URLQueryItem(name: "redirect_to", value: redirectURL),
            URLQueryItem(name: "scopes", value: scopes),
            // Parámetros adicionales para obtener el provider_token y refresh_token
            URLQueryItem(name: "access_type", value: "offline"),
            URLQueryItem(name: "prompt", value: "consent")
        ]
        
        guard let url = components?.url else {
            throw SupabaseError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        return url
    }
    
    // MARK: - Handle Callback
    func handleAuthCallback(url: URL) async throws -> AuthSession {
        // Extraer tokens del URL
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        
        guard let fragment = components?.fragment else {
            throw SupabaseError.invalidCallback
        }
        
        // Parse fragment
        let params = fragment.split(separator: "&").reduce(into: [String: String]()) { result, param in
            let parts = param.split(separator: "=", maxSplits: 1)
            if parts.count == 2 {
                result[String(parts[0])] = String(parts[1])
            }
        }
        
        guard let accessToken = params["access_token"],
              let refreshToken = params["refresh_token"] else {
            throw SupabaseError.missingTokens
        }
        
        // Extraer el provider_token (access token de Google) si está disponible
        let providerToken = params["provider_token"]
        let providerRefreshToken = params["provider_refresh_token"]
        
        print("🔑 Access Token: \(accessToken.prefix(20))...")
        print("🔑 Provider Token: \(providerToken ?? "nil")")
        print("🔑 Provider Refresh Token: \(providerRefreshToken ?? "nil")")
        
        // Guardar los tokens del proveedor
        if let googleToken = providerToken {
            print("✅ Guardando provider_token")
            saveProviderToken(googleToken)
        } else {
            print("⚠️ No se recibió provider_token - Obteniendo de la sesión completa")
            // Si no viene en el callback, intentar obtenerlo del endpoint de sesión
            if let session = try? await fetchSessionWithProviderToken(accessToken: accessToken) {
                if let googleToken = session.providerToken {
                    print("✅ Provider token obtenido de la sesión")
                    saveProviderToken(googleToken)
                }
                if let googleRefreshToken = session.providerRefreshToken {
                    saveProviderRefreshToken(googleRefreshToken)
                }
            }
        }
        
        if let googleRefreshToken = providerRefreshToken {
            saveProviderRefreshToken(googleRefreshToken)
        }
        
        // Obtener información del usuario
        let user = try await fetchUser(accessToken: accessToken)
        
        return AuthSession(
            accessToken: accessToken,
            refreshToken: refreshToken,
            user: user
        )
    }

    // MARK: - Group DTOs
    struct GroupDTO: Codable, Identifiable {
        let id: UUID
        let name: String
        let description: String?
        let imageUrl: String?
        let creatorId: UUID
        let type: String // public, friends_only, followers_only, private
        let createdAt: Date
        
        enum CodingKeys: String, CodingKey {
            case id, name, description
            case imageUrl = "image_url"
            case creatorId = "creator_id"
            case type
            case createdAt = "created_at"
        }
    }
    
    struct GroupMemberDTO: Codable, Identifiable {
        let id: UUID
        let groupId: UUID
        let userId: UUID
        let role: String // admin, moderator, member
        let joinedAt: Date
        
        enum CodingKeys: String, CodingKey {
            case id
            case groupId = "group_id"
            case userId = "user_id"
            case role
            case joinedAt = "joined_at"
            case user // Can be manually decoded or mapped if part of JSON
        }
        
        let user: PostCreator? // Joined user info
    }
    
    // MARK: - Presence
    func setUserActive(_ isActive: Bool) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { return }
              
        let url = URL(string: "\(supabaseURL)/rest/v1/users?id=eq.\(currentUser.id)")!
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        // Update is_active and last_active_at if becoming active
        var body: [String: Any] = ["is_active": isActive]
        if isActive {
            body["last_active_at"] = ISO8601DateFormatter().string(from: Date())
            body["status"] = "available" // Sync legacy status
        } else {
             body["status"] = "offline" // Sync legacy status
        }
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body) 
        let _ = try await URLSession.shared.data(for: request)
    }

    // MARK: - Stories DTOs
    struct StoryDTO: Codable, Identifiable {
        let id: UUID
        let userId: UUID
        let username: String? // Optional because it might come from join
        let nombre: String?
        let apellido: String?
        let avatarUrl: String? // Optional same
        let mediaUrl: String
        let mediaType: String
        let thumbnailUrl: String?
        let caption: String?
        let durationSeconds: Int
        let createdAt: Date
        let expiresAt: Date
        let viewsCount: Int
        let hasViewed: Bool?
        let visibility: String? // Added for visibility control
        let groupId: UUID? // Added for Groups support
        
        enum CodingKeys: String, CodingKey {
            case id
            case userId = "user_id"
            case username
            case nombre
            case apellido
            case avatarUrl = "avatar_url"
            case mediaUrl = "media_url"
            case mediaType = "media_type"
            case thumbnailUrl = "thumbnail_url"
            case caption
            case durationSeconds = "duration_seconds"
            case createdAt = "created_at"
            case expiresAt = "expires_at"
            case viewsCount = "views_count"
            case hasViewed = "has_viewed"
            case visibility
            case groupId = "group_id"
        }
    }
    
    // MARK: - Story Methods
    
    // 1. Upload Story
    func uploadStory(image: UIImage, caption: String?, visibility: String = "followers") async throws -> StoryDTO {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
            throw SupabaseError.missingTokens
        }
        
        // 1. Upload Image
        let filename = "\(currentUser.id)/\(UUID().uuidString).jpg"
        guard let imageData = image.jpegData(compressionQuality: 0.8) else {
            throw SupabaseError.invalidURL
        }
        
        let storageUrl = URL(string: "\(supabaseURL)/storage/v1/object/stories/\(filename)")!
        var storageRequest = URLRequest(url: storageUrl)
        storageRequest.httpMethod = "POST"
        storageRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        storageRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        storageRequest.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        storageRequest.httpBody = imageData
        
        let (_, storageResponse) = try await URLSession.shared.data(for: storageRequest)
        
        guard let httpResponse = storageResponse as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.fetchUserFailed
        }
        
        // Public URL
        let publicUrl = "\(supabaseURL)/storage/v1/object/public/stories/\(filename)"
        
        // 2. Create Story Record
        let dbUrl = URL(string: "\(supabaseURL)/rest/v1/stories")!
        var dbRequest = URLRequest(url: dbUrl)
        dbRequest.httpMethod = "POST"
        dbRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        dbRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        dbRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        dbRequest.setValue("return=representation", forHTTPHeaderField: "Prefer")
        
        let body: [String: Any] = [
            "user_id": currentUser.id,
            "media_url": publicUrl,
            "media_type": "image", // Hardcoded for now, support video later
            "caption": caption as Any,
            "duration_seconds": 5,
            "visibility": visibility
        ]
        
        dbRequest.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (data, dbResponse) = try await URLSession.shared.data(for: dbRequest)
        
        guard let dbHttpResponse = dbResponse as? HTTPURLResponse,
              (200...299).contains(dbHttpResponse.statusCode) else {
             throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let stories = try decoder.decode([StoryDTO].self, from: data)
        guard let story = stories.first else { throw SupabaseError.fetchUserFailed }
        return story
    }
    
    // 2. Fetch Active Stories (Self)
    func fetchMyActiveStories() async throws -> [StoryDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
             throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/rpc/get_active_user_stories")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = ["p_user_id": currentUser.id]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([StoryDTO].self, from: data)
    }
    
    // 3. Fetch Friends' Stories (All visible stories from followed users)
    func fetchFriendsStories() async throws -> [StoryDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
             throw SupabaseError.missingTokens
        }
        
        print("🔍 [STORIES] Fetching stories for followed users...")
        
        // Step 1: Get list of followed user IDs from 'followers' table
        let followersUrl = URL(string: "\(supabaseURL)/rest/v1/followers?follower_id=eq.\(currentUser.id)&select=followed_id")!
        var followersReq = URLRequest(url: followersUrl)
        followersReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        followersReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (followersData, followersResponse) = try await URLSession.shared.data(for: followersReq)
        guard let followersHttp = followersResponse as? HTTPURLResponse, followersHttp.statusCode == 200 else {
            print("❌ [STORIES] Failed to fetch followed users")
            return []
        }
        
        struct FollowedIdRow: Decodable { let followed_id: UUID }
        let followedIds = try JSONDecoder().decode([FollowedIdRow].self, from: followersData).map { $0.followed_id.uuidString }
        
        if followedIds.isEmpty {
            print("ℹ️ [STORIES] No followed users, returning empty stories")
            return []
        }
        
        print("📦 [STORIES] Fetching stories from \(followedIds.count) followed users")
        
        // Step 2: Fetch active stories from those users
        // Format: user_id=in.(id1,id2,id3)
        // Query for active stories (valid for 24h)
        // select: *,user:users(username,nombre,apellido,avatar_url)
        let idsParam = followedIds.joined(separator: ",")
        let storiesUrlString = "\(supabaseURL)/rest/v1/stories?user_id=in.(\(idsParam))&is_active=eq.true&expires_at=gt.now()&select=*,user:users(username,nombre,apellido,avatar_url)&order=created_at.desc"
        
        guard let storiesUrl = URL(string: storiesUrlString) else {
            print("❌ [STORIES] Invalid URL")
            return []
        }
        
        var storiesReq = URLRequest(url: storiesUrl)
        storiesReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        storiesReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (storiesData, storiesResponse) = try await URLSession.shared.data(for: storiesReq)
        guard let storiesHttp = storiesResponse as? HTTPURLResponse, storiesHttp.statusCode == 200 else {
            let errStr = String(data: storiesData, encoding: .utf8) ?? "Unknown"
            print("❌ [STORIES] Error fetching stories: \(errStr)")
            return []
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let stories = try decoder.decode([StoryDTO].self, from: storiesData)
        print("✅ [STORIES] Fetched \(stories.count) stories from friends")
        return stories
    }
    
    // 4. Fetch Stories for a specific user (Public/Followed check handled by RLS)
    func fetchUserStories(userId: UUID) async throws -> [StoryDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
             throw SupabaseError.missingTokens
        }
        
        // We can reuse get_active_user_stories since RLS policies should handle visibility,
        // OR simply query the stories table directly filtering by user_id and is_active.
        // Let's query table directly for simplicity and robustness with RLS.
        
        // Table: public.stories
        // Filter: user_id = userId & is_active = true & expires_at > now()
        
        var components = URLComponents(string: "\(supabaseURL)/rest/v1/stories")!
        components.queryItems = [
            URLQueryItem(name: "user_id", value: "eq.\(userId)"),
            URLQueryItem(name: "is_active", value: "eq.true"),
            URLQueryItem(name: "expires_at", value: "gt.now"), // Ensure strictly not expired
            URLQueryItem(name: "select", value: "*"),
            URLQueryItem(name: "order", value: "created_at.asc")
        ]
        
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let stories = try decoder.decode([StoryDTO].self, from: data)
        
        // Enrich with user info locally if needed, but usually we have user context.
        // If we need avatar/username, we might need a join.
        // For UserProfileView we already have the User.
        return stories
    }
    
    // 5. Check if user has active stories (Lightweight check)
    func hasActiveStories(userId: UUID) async throws -> Bool {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { return false }
        
        var components = URLComponents(string: "\(supabaseURL)/rest/v1/stories")!
        components.queryItems = [
            URLQueryItem(name: "user_id", value: "eq.\(userId)"),
            URLQueryItem(name: "is_active", value: "eq.true"),
            URLQueryItem(name: "expires_at", value: "gt.now"),
            URLQueryItem(name: "select", value: "count"),
            URLQueryItem(name: "limit", value: "1"),
             URLQueryItem(name: "head", value: "true") // HEAD request just for count/existence
        ]
        
        var request = URLRequest(url: components.url!)
        request.httpMethod = "GET" // Or HEAD, but GET with count working
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("count", forHTTPHeaderField: "Prefer") // count=exact
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse,
           let contentRange = httpResponse.value(forHTTPHeaderField: "Content-Range"),
           let countString = contentRange.split(separator: "/").last,
           let count = Int(countString) {
            return count > 0
        }
        return false
    }

    // 6. Mark Story as Viewed
    func markStoryAsViewed(storyId: UUID) async {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { return }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/story_views")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        
        let body: [String: Any] = [
            "story_id": storyId.uuidString,
            "viewer_id": currentUser.id
        ]
        
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        // Fire and forget (or minimal checking)
        _ = try? await URLSession.shared.data(for: request)
    }
    private func fetchUser(accessToken: String) async throws -> User {
        let userURL = URL(string: "\(supabaseURL)/auth/v1/user")!
        
        var request = URLRequest(url: userURL)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        let user = try decoder.decode(User.self, from: data)
        
        return user
    }
    
    // MARK: - Fetch Session with Provider Token
    private func fetchSessionWithProviderToken(accessToken: String) async throws -> SessionResponse {
        let sessionURL = URL(string: "\(supabaseURL)/auth/v1/user")!
        
        var request = URLRequest(url: sessionURL)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw SupabaseError.fetchUserFailed
        }
        
        // Intentar parsear la respuesta completa para buscar provider_token
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            print("📦 Session response: \(json.keys)")
            
            // Buscar en diferentes ubicaciones posibles
            var providerToken: String?
            var providerRefreshToken: String?
            
            // Opción 1: Directamente en la respuesta
            if let token = json["provider_token"] as? String {
                providerToken = token
            }
            
            if let refreshToken = json["provider_refresh_token"] as? String {
                providerRefreshToken = refreshToken
            }
            
            // Opción 2: Dentro de app_metadata
            if let appMetadata = json["app_metadata"] as? [String: Any] {
                if let token = appMetadata["provider_token"] as? String {
                    providerToken = token
                }
                if let refreshToken = appMetadata["provider_refresh_token"] as? String {
                    providerRefreshToken = refreshToken
                }
            }
            
            // Opción 3: Dentro de user_metadata
            if let userMetadata = json["user_metadata"] as? [String: Any] {
                if let token = userMetadata["provider_token"] as? String {
                    providerToken = token
                }
                if let refreshToken = userMetadata["provider_refresh_token"] as? String {
                    providerRefreshToken = refreshToken
                }
            }
            
            // Opción 4: Dentro de identities
            if let identities = json["identities"] as? [[String: Any]],
               let googleIdentity = identities.first(where: { ($0["provider"] as? String) == "google" }),
               let identityData = googleIdentity["identity_data"] as? [String: Any] {
                
                if let token = identityData["provider_token"] as? String {
                    providerToken = token
                }
                if let refreshToken = identityData["provider_refresh_token"] as? String {
                    providerRefreshToken = refreshToken
                }
            }
            
            return SessionResponse(
                providerToken: providerToken,
                providerRefreshToken: providerRefreshToken
            )
        }
        
        throw SupabaseError.fetchUserFailed
    }
    
        // MARK: - Sign Out
    func signOut() {
        UserDefaults.standard.removeObject(forKey: "supabase_access_token")
        UserDefaults.standard.removeObject(forKey: "supabase_refresh_token")
        UserDefaults.standard.removeObject(forKey: "supabase_user")
        UserDefaults.standard.removeObject(forKey: "google_provider_token")
        UserDefaults.standard.removeObject(forKey: "google_provider_refresh_token")
    }
    
    // MARK: - Save Session
    func saveSession(_ session: AuthSession) {
        UserDefaults.standard.set(session.accessToken, forKey: "supabase_access_token")
        UserDefaults.standard.set(session.refreshToken, forKey: "supabase_refresh_token")
        
        if let userData = try? JSONEncoder().encode(session.user) {
            UserDefaults.standard.set(userData, forKey: "supabase_user")
        }
    }
    
    // MARK: - Load Session
    func loadSession() -> AuthSession? {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let refreshToken = UserDefaults.standard.string(forKey: "supabase_refresh_token"),
              let userData = UserDefaults.standard.data(forKey: "supabase_user"),
              let user = try? JSONDecoder().decode(User.self, from: userData) else {
            return nil
        }
        
        return AuthSession(accessToken: accessToken, refreshToken: refreshToken, user: user)
    }
    
    // MARK: - Session Refresh
    func refreshSession() async throws {
        print("🔄 Attempting to refresh session...")
        guard let refreshToken = UserDefaults.standard.string(forKey: "supabase_refresh_token") else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/auth/v1/token?grant_type=refresh_token")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = ["refresh_token": refreshToken]
        
        let (data, response): (Data, URLResponse)
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
            (data, response) = try await URLSession.shared.data(for: request)
        } catch {
            // Handle network errors gracefully
            let nsError = error as NSError
            let networkErrors = [NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost, NSURLErrorTimedOut]
            if networkErrors.contains(nsError.code) {
                print("⚠️ Network error during refresh: \(error.localizedDescription). Keeping local session.")
                throw error // Propagate error but DO NOT sign out
            }
            
            // If it's not a specific network error, re-throw
            print("❌ Refresh failed with unexpected error: \(error)")
            throw error
        }
        
        guard let httpResponse = response as? HTTPURLResponse else {
            print("❌ Refresh failed: Invalid HTTP response.")
            throw SupabaseError.fetchUserFailed // Or a more specific error
        }
        
        guard httpResponse.statusCode == 200 else {
            print("❌ Refresh failed with status: \(httpResponse.statusCode)")
            // Only sign out for client errors (4xx)
            if (400...499).contains(httpResponse.statusCode) {
                signOut()
                throw SupabaseError.missingTokens // Indicates session is invalid
            } else {
                // For server errors (5xx) or other non-200, re-throw without signing out
                // This allows for retries or temporary issues
                throw SupabaseError.fetchUserFailed // Or a more specific error based on status code
            }
        }
        
        struct RefreshResponse: Decodable {
            let accessToken: String
            let refreshToken: String
            let user: User
            
            enum CodingKeys: String, CodingKey {
                case accessToken = "access_token"
                case refreshToken = "refresh_token"
                case user
            }
        }
        
        do {
            let decoder = JSONDecoder()
            let refreshResponse = try decoder.decode(RefreshResponse.self, from: data)
            
            let session = AuthSession(
                accessToken: refreshResponse.accessToken,
                refreshToken: refreshResponse.refreshToken,
                user: refreshResponse.user
            )
            saveSession(session)
            print("✅ Session refreshed successfully!")
        } catch {
            print("❌ Error decoding refresh response: \(error)")
            throw error
        }
    }
    
    // MARK: - Provider Tokens
    private func saveProviderToken(_ token: String) {
        print("💾 Guardando token en UserDefaults...")
        print("💾 Token a guardar: \(token.prefix(30))...")
        UserDefaults.standard.set(token, forKey: "google_provider_token")
        UserDefaults.standard.synchronize()
        
        // Verificar que se guardó
        if let saved = UserDefaults.standard.string(forKey: "google_provider_token") {
            print("✅ Token verificado en UserDefaults: \(saved.prefix(30))...")
        } else {
            print("❌ ERROR: Token NO se guardó en UserDefaults")
        }
    }
    
    private func saveProviderRefreshToken(_ token: String) {
        UserDefaults.standard.set(token, forKey: "google_provider_refresh_token")
    }
    
    func getProviderToken() -> String? {
        return UserDefaults.standard.string(forKey: "google_provider_token")
    }
    
    func getProviderRefreshToken() -> String? {
        return UserDefaults.standard.string(forKey: "google_provider_refresh_token")
    }
    // MARK: - Social Features (Search & Chat)
    
    // Search in 'users' table (acting as profiles)
    func searchProfiles(query: String) async throws -> [ProfileDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let encodedQuery = query.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? query
        // Querying 'users' table
        let urlString = "\(supabaseURL)/rest/v1/users?or=(username.ilike.*\(encodedQuery)*,nombre.ilike.*\(encodedQuery)*)&select=*"
        
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            print("Error searching users: \(String(data: data, encoding: .utf8) ?? "Unknown")")
            throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([ProfileDTO].self, from: data)
    }
    
    // Fetch recent/available users for default view
    func fetchAvailableUsers() async throws -> [ProfileDTO] {
        var accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
        
        let urlString = "\(supabaseURL)/rest/v1/users?select=*&order=last_seen.desc.nullslast&limit=50"
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        func performRequest(token: String) async throws -> (Data, HTTPURLResponse) {
            var request = URLRequest(url: url)
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                 throw SupabaseError.fetchUserFailed
            }
            return (data, httpResponse)
        }
        
        var (data, response) = try await performRequest(token: accessToken)
        
        if response.statusCode == 401 {
            try await refreshSession()
            accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
            (data, response) = try await performRequest(token: accessToken)
        }
        
        guard response.statusCode == 200 else {
            print("Error fetching available users: \(String(data: data, encoding: .utf8) ?? "Unknown")")
            throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([ProfileDTO].self, from: data)
    }


    // Sending to 'direct_messages' table
    func sendMessage(to targetId: String, content: String, replyToId: Int? = nil, replyContext: String? = nil, replyContextSender: String? = nil) async throws -> MessageDTO {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/direct_messages")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=representation", forHTTPHeaderField: "Prefer")
        
        var body: [String: Any] = [
            "sender_id": currentUser.id,
            "receiver_id": targetId,
            "content": content,
            "is_temporary": true
        ]
        
        // Always send the ID if we have it (FK constraint is removed on DB)
        if let replyId = replyToId {
            body["reply_to_id"] = replyId
        }
        
        // Included Snapshot Data for Robust Rendering
        if let ctx = replyContext {
            body["reply_context_content"] = ctx
        }
        if let senderName = replyContextSender {
            body["reply_context_sender_username"] = senderName
        }
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            if let str = String(data: data, encoding: .utf8) {
                print("❌ Send Error: \(str)")
            }
            throw SupabaseError.saveFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let messages = try decoder.decode([MessageDTO].self, from: data)
        guard let message = messages.first else { throw SupabaseError.fetchUserFailed }
        return message
    }
    
    // Fetching from 'direct_messages' table
    func fetchMessages(with userId: String) async throws -> [MessageDTO] {
        var accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
        guard let currentUser = loadSession()?.user else { throw SupabaseError.missingTokens }
        
        let myId = currentUser.id
        // FIX: Removing the server-side join for reply_to_message which was causing PGRST200 errors.
        // We will fetch the data flat and link the threads manually in Swift.
        // We still fetch sender/receiver info as that works fine.
        
        let select = "*,sender:users!direct_messages_sender_id_fkey(*),receiver:users!direct_messages_receiver_id_fkey(*)"
        let urlString = "\(supabaseURL)/rest/v1/direct_messages?or=(and(sender_id.eq.\(myId),receiver_id.eq.\(userId)),and(sender_id.eq.\(userId),receiver_id.eq.\(myId)))&select=\(select)&order=created_at.asc"
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        func performRequest(token: String) async throws -> (Data, HTTPURLResponse) {
            var request = URLRequest(url: url)
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                 throw SupabaseError.fetchUserFailed
            }
            return (data, httpResponse)
        }
        
        var (data, response) = try await performRequest(token: accessToken)
        
        if response.statusCode == 401 {
            try await refreshSession()
            accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
            (data, response) = try await performRequest(token: accessToken)
        }
        
        guard response.statusCode == 200 else {
            let errorMsg = String(data: data, encoding: .utf8) ?? "Unknown error"
            print("❌ Error fetching messages: \(errorMsg)")
            throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        
        do {
            let rawMessages = try decoder.decode([MessageDTO].self, from: data)
            
            // MANUAL THREAD LINKING (Hydration)
            // Create a lookup dictionary
            let messageMap = Dictionary(uniqueKeysWithValues: rawMessages.map { ($0.id, $0) })
            
            // Re-create messages with populated replyToMessage
            let linkedMessages = rawMessages.map { msg -> MessageDTO in
                guard let replyId = msg.replyToId else { return msg }
                
                var parentMsg: MessageDTO? = messageMap[replyId]
                
                // If parent missing in DB response, verify if we have snapshot data
                if parentMsg == nil, let snapContent = msg.replyContextContent {
                    // Reconstruct a "Ghost" Parent for display
                    let ghostSender = PostCreator(username: msg.replyContextSenderUsername ?? "Usuario", nombre: nil, apellido: nil, avatar_url: nil)
                    
                    parentMsg = MessageDTO(
                        id: replyId,
                        senderId: UUID(), // Dummy
                        receiverId: UUID(), // Dummy
                        content: snapContent,
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
                }
                
                guard let finalParent = parentMsg else { return msg }
                
                return MessageDTO(
                    id: msg.id,
                    senderId: msg.senderId,
                    receiverId: msg.receiverId,
                    content: msg.content,
                    isTemporary: msg.isTemporary,
                    seenAt: msg.seenAt,
                    createdAt: msg.createdAt,
                    status: msg.status,
                    replyToId: msg.replyToId,
                    replyContextContent: msg.replyContextContent,
                    replyContextSenderUsername: msg.replyContextSenderUsername,
                    sender: msg.sender,
                    receiver: msg.receiver,
                    replyToMessage: BoxedMessageDTO(message: finalParent)
                )
            }
            
            return linkedMessages
            
        } catch {
            print("❌ Error decoding messages: \(error)")
             if let str = String(data: data, encoding: .utf8) {
                 print("📦 Raw Response: \(str)")
             }
            throw error
        }
    }
    
    // MARK: - Ephemeral Messaging
    
    /// Marks a message as seen (sets seen_at timestamp) and deletes it from server if temporary
    func markMessageSeenAndDelete(messageId: UUID) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        // 1. Update seen_at timestamp
        let patchUrl = URL(string: "\(supabaseURL)/rest/v1/direct_messages?id=eq.\(messageId.uuidString)")!
        var patchReq = URLRequest(url: patchUrl)
        patchReq.httpMethod = "PATCH"
        patchReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        patchReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        patchReq.setValue("application/json", forHTTPHeaderField: "Content-Type")
        patchReq.setValue("return=representation", forHTTPHeaderField: "Prefer")
        
        let nowISO = ISO8601DateFormatter().string(from: Date())
        let patchBody: [String: Any] = ["seen_at": nowISO]
        patchReq.httpBody = try JSONSerialization.data(withJSONObject: patchBody)
        
        let (patchData, patchResp) = try await URLSession.shared.data(for: patchReq)
        guard let patchHttp = patchResp as? HTTPURLResponse, (200...299).contains(patchHttp.statusCode) else {
            print("⚠️ Failed to mark message as seen")
            return
        }
        
        // 2. Check if message is temporary before deleting
        struct MsgCheck: Decodable {
            let isTemporary: Bool?
            enum CodingKeys: String, CodingKey { case isTemporary = "is_temporary" }
        }
        if let msgs = try? JSONDecoder().decode([MsgCheck].self, from: patchData), let msg = msgs.first, msg.isTemporary == true {
            // 3. Delete the message
            let delUrl = URL(string: "\(supabaseURL)/rest/v1/direct_messages?id=eq.\(messageId.uuidString)")!
            var delReq = URLRequest(url: delUrl)
            delReq.httpMethod = "DELETE"
            delReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            delReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            
            let (_, delResp) = try await URLSession.shared.data(for: delReq)
            if let delHttp = delResp as? HTTPURLResponse, (200...299).contains(delHttp.statusCode) {
                print("🗑️ Ephemeral message \(messageId) deleted after viewing")
            }
        }
    }
    
    /// Send a direct message (now defaults to temporary/ephemeral)
    func sendDirectMessage(to receiverId: UUID, content: String, isTemporary: Bool = true) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { throw SupabaseError.missingTokens }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/direct_messages")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "sender_id": currentUser.id,
            "receiver_id": receiverId.uuidString,
            "content": content,
            "is_temporary": isTemporary
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (_, response) = try await URLSession.shared.data(for: request)
        
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw SupabaseError.saveFailed
        }
    }
    
    // MARK: - Message Status (WhatsApp-style)
    
    /// Mark message as delivered (double gray check)
    func markMessageDelivered(messageId: Int64) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/direct_messages?id=eq.\(messageId)")!
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "status": "delivered",
            "delivered_at": ISO8601DateFormatter().string(from: Date())
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw SupabaseError.saveFailed
        }
    }
    
    /// Mark message as read (double blue check)
    func markMessageRead(messageId: Int64) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/direct_messages?id=eq.\(messageId)")!
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "status": "read",
            "is_read": true,
            "seen_at": ISO8601DateFormatter().string(from: Date())
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw SupabaseError.saveFailed
        }
    }
    
    /// Delete message from server (after local save)
    func deleteDirectMessage(messageId: Int64) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/direct_messages?id=eq.\(messageId)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw SupabaseError.saveFailed
        }
    }
    
    /// Fetch direct messages with a specific user
    func fetchDirectMessages(with partnerId: UUID) async throws -> [DirectMessageDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
            throw SupabaseError.missingTokens
        }
        
        // Fetch messages where (sender=me AND receiver=partner) OR (sender=partner AND receiver=me)
        let currentId = currentUser.id
        let urlString = "\(supabaseURL)/rest/v1/direct_messages?or=(and(sender_id.eq.\(currentId),receiver_id.eq.\(partnerId.uuidString)),and(sender_id.eq.\(partnerId.uuidString),receiver_id.eq.\(currentId)))&order=created_at.asc"
        
        guard let url = URL(string: urlString) else {
            throw SupabaseError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([DirectMessageDTO].self, from: data)
    }

    // Fetch Unique Conversations (Simulated Inbox)
    func fetchInbox() async throws -> [ProfileDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
             throw SupabaseError.missingTokens
        }
        
        // 1. Get all messages where I am the sender OR receiver
        let urlString = "\(supabaseURL)/rest/v1/direct_messages?or=(sender_id.eq.\(currentUser.id),receiver_id.eq.\(currentUser.id))&select=sender_id,receiver_id,created_at&order=created_at.desc"
        
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            print("❌ [INBOX] HTTP Error: \((response as? HTTPURLResponse)?.statusCode ?? 0)")
            throw SupabaseError.fetchUserFailed
        }
        
        // Custom struct to extract both sender and receiver IDs
        struct ConversationMsg: Decodable {
            let sender_id: UUID
            let receiver_id: UUID
        }
        
        let messages = try JSONDecoder().decode([ConversationMsg].self, from: data)
        
        // Extract unique conversation partners (not myself)
        var partnerIds = Set<String>()
        for msg in messages {
            if msg.sender_id.uuidString.lowercased() != currentUser.id.lowercased() {
                partnerIds.insert(msg.sender_id.uuidString)
            }
            if msg.receiver_id.uuidString.lowercased() != currentUser.id.lowercased() {
                partnerIds.insert(msg.receiver_id.uuidString)
            }
        }
        
        if partnerIds.isEmpty { return [] }
        
        // 2. Fetch Profiles for these partners
        let idsString = partnerIds.joined(separator: ",")
        let usersUrlString = "\(supabaseURL)/rest/v1/users?id=in.(\(idsString))&select=*"
        
        guard let usersUrl = URL(string: usersUrlString) else { throw SupabaseError.invalidURL }
        var usersRequest = URLRequest(url: usersUrl)
        usersRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        usersRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (usersData, usersResponse) = try await URLSession.shared.data(for: usersRequest)
        
        guard let usersHttpResponse = usersResponse as? HTTPURLResponse, usersHttpResponse.statusCode == 200 else {
            throw SupabaseError.fetchUserFailed
        }
        
        let profiles = try JSONDecoder().decode([ProfileDTO].self, from: usersData)
        print("✅ [INBOX] Loaded \(profiles.count) conversations")
        return profiles
    }

    
    // Fetch Followed Users (Users I am following)
    func fetchFollowedUsers() async throws -> [ProfileDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
            throw SupabaseError.missingTokens
        }
        
        // Use 'followers' table where I am the follower
        let urlString = "\(supabaseURL)/rest/v1/followers?follower_id=eq.\(currentUser.id)&select=followed:users!followers_followed_id_fkey(*)"
        
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
             throw SupabaseError.fetchUserFailed
        }
        
        struct FollowedRow: Decodable { let followed: ProfileDTO }
        let rows = try JSONDecoder().decode([FollowedRow].self, from: data)
        return rows.map { $0.followed }
    }
    
    // Follow a user
    func followUser(targetId: UUID) async throws {
        print("🔵 [FOLLOW] Iniciando followUser para targetId: \(targetId.uuidString)")
        
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/followers")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=minimal", forHTTPHeaderField: "Prefer") // Don't need body back
        
        let body: [String: Any] = [
            "follower_id": currentUser.id,
            "followed_id": targetId.uuidString,
            "notifications_enabled": true
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
             throw SupabaseError.fetchUserFailed
        }
        
        print("📥 [FOLLOW] Status: \(httpResponse.statusCode)")
        
        // 201 Created or 409 Conflict (already following) are fine
        if (200...299).contains(httpResponse.statusCode) {
            print("✅ [FOLLOW] Follow exitoso")
            return
        }
        
        if httpResponse.statusCode == 409 {
             print("⚠️ [FOLLOW] Ya seguías a este usuario (Conflicto ignorado)")
             return
        }
        
        let errorString = String(data: data, encoding: .utf8) ?? "Unknown"
        print("❌ [FOLLOW] Error: \(errorString)")
        throw SupabaseError.uploadFailed
    }
    
    // Unfollow a user
    func unfollowUser(targetId: UUID) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/followers?follower_id=eq.\(currentUser.id)&followed_id=eq.\(targetId.uuidString)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             throw SupabaseError.saveFailed
        }
    }
    
    // Remove Friend (Unfriend)
    func removeFriend(targetId: UUID) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        // Use RPC to ensure bidirectional deletion (A->B and B->A)
        struct Params: Encodable {
            let target_friend_id: UUID
        }
        
        let params = Params(target_friend_id: targetId)
        let url = URL(string: "\(supabaseURL)/rest/v1/rpc/remove_friend")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        request.httpBody = try JSONEncoder().encode(params)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             let err = String(data: data, encoding: .utf8) ?? ""
             print("❌ [REMOVE_FRIEND] Error: \(err)")
             throw SupabaseError.saveFailed
        }
    }
        

    
    // Send Friend Request
    func sendFriendRequest(targetId: UUID) async throws {
        print("🟣 [FRIEND_REQ] Iniciando sendFriendRequest para targetId: \(targetId.uuidString)")
        
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { throw SupabaseError.missingTokens }
        
        // Use friend_requests table
        let url = URL(string: "\(supabaseURL)/rest/v1/friend_requests")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        
        let body: [String: Any] = [
            "sender_id": currentUser.id,
            "receiver_id": targetId.uuidString,
            "status": "pending"
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let http = response as? HTTPURLResponse else { throw SupabaseError.fetchUserFailed }
        
        if (200...299).contains(http.statusCode) {
            print("✅ [FRIEND_REQ] Solicitud enviada.")
            return
        }
        
        // Conflict (already sent)
        if http.statusCode == 409 {
             print("⚠️ [FRIEND_REQ] Ya existe solicitud.")
             return
        }
        
        let err = String(data: data, encoding: .utf8) ?? ""
        print("❌ [FRIEND_REQ] Error: \(err)")
        throw SupabaseError.uploadFailed
    }
    
    // Fetch Pending Incoming Requests
    func fetchIncomingFriendRequests() async throws -> [(requestId: UUID, user: ProfileDTO)] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { 
            print("❌ [INCOMING_REQ] No access token or user")
            throw SupabaseError.missingTokens 
        }
              
        // Select from friend_requests where receiver_id = me AND status = pending
        // Join with sender:users to get profile
        let urlString = "\(supabaseURL)/rest/v1/friend_requests?receiver_id=eq.\(currentUser.id)&status=eq.pending&select=id,sender:users!friend_requests_sender_id_fkey(*)"
        
        print("🔍 [INCOMING_REQ] Query URL: \(urlString)")
        
        guard let url = URL(string: urlString) else { 
            print("❌ [INCOMING_REQ] Invalid URL")
            return [] 
        }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { 
            print("❌ [INCOMING_REQ] No HTTP response")
            return [] 
        }
        
        print("📥 [INCOMING_REQ] Status: \(http.statusCode)")
        
        if http.statusCode != 200 {
            let errorStr = String(data: data, encoding: .utf8) ?? "No details"
            print("❌ [INCOMING_REQ] Error: \(errorStr)")
            return []
        }
        
        if let jsonStr = String(data: data, encoding: .utf8) {
            print("📦 [INCOMING_REQ] Response: \(jsonStr)")
        }
        
        struct ReqRow: Decodable {
            let id: UUID
            let sender: ProfileDTO
        }
        
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let rows = try decoder.decode([ReqRow].self, from: data)
            print("✅ [INCOMING_REQ] Encontradas \(rows.count) solicitudes entrantes")
            return rows.map { ($0.id, $0.sender) }
        } catch {
            print("❌ [INCOMING_REQ] Decode error: \(error)")
            return []
        }
    }
    
    // Accept Friend Request
    func acceptFriendRequest(requestId: UUID, senderId: UUID) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { throw SupabaseError.missingTokens }
        
        struct Params: Encodable {
            let request_id: UUID
        }
        
        let params = Params(request_id: requestId)
        let url = URL(string: "\(supabaseURL)/rest/v1/rpc/accept_friend_request")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        request.httpBody = try JSONEncoder().encode(params)
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
             let err = String(data: data, encoding: .utf8) ?? ""
             print("❌ [ACCEPT_REQ] Error: \(err)")
             throw SupabaseError.saveFailed
        }
    }
    
    // Reject Friend Request
    func rejectFriendRequest(requestId: UUID) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { throw SupabaseError.missingTokens }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/friend_requests?id=eq.\(requestId.uuidString)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw SupabaseError.saveFailed
        }
    }
    
    // Fetch Pending Outgoing Requests
    func fetchOutgoingFriendRequests() async throws -> [(requestId: UUID, user: ProfileDTO)] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { 
            print("❌ [OUTGOING_REQ] No access token or user")
            throw SupabaseError.missingTokens 
        }
              
        // Select from friend_requests where sender_id = me AND status = pending
        let urlString = "\(supabaseURL)/rest/v1/friend_requests?sender_id=eq.\(currentUser.id)&status=eq.pending&select=id,receiver:users!friend_requests_receiver_id_fkey(*)"
        
        print("🔍 [OUTGOING_REQ] Query URL: \(urlString)")
        
        guard let url = URL(string: urlString) else { 
            print("❌ [OUTGOING_REQ] Invalid URL")
            return [] 
        }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { 
            print("❌ [OUTGOING_REQ] No HTTP response")
            return [] 
        }
        
        print("📥 [OUTGOING_REQ] Status: \(http.statusCode)")
        
        if http.statusCode != 200 {
            let errorStr = String(data: data, encoding: .utf8) ?? "No details"
            print("❌ [OUTGOING_REQ] Error: \(errorStr)")
            return []
        }
        
        if let jsonStr = String(data: data, encoding: .utf8) {
            print("📦 [OUTGOING_REQ] Response: \(jsonStr)")
        }
        
        struct OutReqRow: Decodable {
            let id: UUID
            let receiver: ProfileDTO
        }
        
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let rows = try decoder.decode([OutReqRow].self, from: data)
            print("✅ [OUTGOING_REQ] Encontradas \(rows.count) solicitudes enviadas")
            return rows.map { ($0.id, $0.receiver) }
        } catch {
            print("❌ [OUTGOING_REQ] Decode error: \(error)")
            return []
        }
    }
    
    // MARK: - Friends & Followers (New Schema)
    
    // Fetch Friends (From 'friends' table)
    // Now simpler: just query friends table where user_id = me.
    // (Assuming double-entry logic as decided in migration script, so A->B implies B->A exists)
    func fetchFriends(userId: UUID? = nil) async throws -> [ProfileDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { 
            print("❌ [FRIENDS] No access token")
            throw SupabaseError.missingTokens 
        }

        let targetId: String
        if let uid = userId {
            targetId = uid.uuidString
        } else {
            guard let currentUser = loadSession()?.user else { 
                print("❌ [FRIENDS] No current user")
                throw SupabaseError.missingTokens 
            }
            targetId = currentUser.id
        }

        // Query: user_id = targetId, status = active
        // Select friend_id (which points to users table)
        let urlString = "\(supabaseURL)/rest/v1/friends?user_id=eq.\(targetId)&status=eq.active&select=friend:users!friends_friend_id_fkey(*)"
        
        print("🔍 [FRIENDS] Query URL: \(urlString)")
        
        guard let url = URL(string: urlString) else { 
            print("❌ [FRIENDS] Invalid URL")
            return [] 
        }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { 
            print("❌ [FRIENDS] No HTTP response")
            return [] 
        }
        
        print("📥 [FRIENDS] Status: \(httpResponse.statusCode)")
        
        if httpResponse.statusCode != 200 {
            let errorStr = String(data: data, encoding: .utf8) ?? "No details"
            print("❌ [FRIENDS] Error: \(errorStr)")
            return []
        }
        
        if let jsonStr = String(data: data, encoding: .utf8) {
            print("📦 [FRIENDS] Response: \(jsonStr)")
        }
        
        struct FriendRow: Decodable { let friend: ProfileDTO }
        
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let rows = try decoder.decode([FriendRow].self, from: data)
            print("✅ [FRIENDS] Encontrados \(rows.count) amigos")
            return rows.map { $0.friend }
        } catch {
            print("❌ [FRIENDS] Decode error: \(error)")
            return []
        }
    }
    
    // Alias for compatibility if needed, but we should switch usage.
    func fetchMutualFriends() async throws -> [ProfileDTO] {
        return try await fetchFriends()
    }

    // Fetch Following (From 'followers' table where follower_id = me)
    func fetchFollowingUsers(userId: UUID? = nil) async throws -> [ProfileDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { 
            print("❌ [FOLLOWING] No access token")
            throw SupabaseError.missingTokens 
        }

        let targetId: String
        if let uid = userId {
            targetId = uid.uuidString
        } else {
            guard let currentUser = loadSession()?.user else { 
                print("❌ [FOLLOWING] No current user")
                throw SupabaseError.missingTokens 
            }
            targetId = currentUser.id
        }

        let urlString = "\(supabaseURL)/rest/v1/followers?follower_id=eq.\(targetId)&select=followed:users!followers_followed_id_fkey(*)"
        
        print("🔍 [FOLLOWING] Query URL: \(urlString)")
        
        guard let url = URL(string: urlString) else { 
            print("❌ [FOLLOWING] Invalid URL")
            return [] 
        }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { 
            print("❌ [FOLLOWING] No HTTP response")
            return [] 
        }
        
        print("📥 [FOLLOWING] Status: \(httpResponse.statusCode)")
        
        if httpResponse.statusCode != 200 {
            let errorStr = String(data: data, encoding: .utf8) ?? "No details"
            print("❌ [FOLLOWING] Error: \(errorStr)")
            return []
        }
        
        if let jsonStr = String(data: data, encoding: .utf8) {
            print("📦 [FOLLOWING] Response: \(jsonStr)")
        }
        
        struct FollowingRow: Decodable { let followed: ProfileDTO }
        
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let rows = try decoder.decode([FollowingRow].self, from: data)
            print("✅ [FOLLOWING] Encontrados \(rows.count) usuarios siguiendo")
            return rows.map { $0.followed }
        } catch {
            print("❌ [FOLLOWING] Decode error: \(error)")
            return []
        }
    }
    
    // Fetch Followers (From 'followers' table where followed_id = me)
    func fetchFollowers(of targetId: UUID? = nil) async throws -> [ProfileDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { 
            print("❌ [FOLLOWERS] No access token")
            throw SupabaseError.missingTokens 
        }

        let userId: String
        if let target = targetId {
            userId = target.uuidString
        } else {
            guard let currentUser = loadSession()?.user else { 
                print("❌ [FOLLOWERS] No current user")
                throw SupabaseError.missingTokens 
            }
            userId = currentUser.id
        }

        let urlString = "\(supabaseURL)/rest/v1/followers?followed_id=eq.\(userId)&select=follower:users!followers_follower_id_fkey(*)"
        
        print("🔍 [FOLLOWERS] Query URL: \(urlString)")
        
        guard let url = URL(string: urlString) else { 
            print("❌ [FOLLOWERS] Invalid URL")
            return [] 
        }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else { 
            print("❌ [FOLLOWERS] No HTTP response")
            return [] 
        }
        
        print("📥 [FOLLOWERS] Status: \(httpResponse.statusCode)")
        
        if httpResponse.statusCode != 200 {
            let errorStr = String(data: data, encoding: .utf8) ?? "No details"
            print("❌ [FOLLOWERS] Error: \(errorStr)")
            return []
        }
        
        if let jsonStr = String(data: data, encoding: .utf8) {
            print("📦 [FOLLOWERS] Response: \(jsonStr)")
        }
        
        struct FollowerRow: Decodable { let follower: ProfileDTO }
        
        do {
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            let rows = try decoder.decode([FollowerRow].self, from: data)
            print("✅ [FOLLOWERS] Encontrados \(rows.count) seguidores")
            return rows.map { $0.follower }
        } catch {
            print("❌ [FOLLOWERS] Decode error: \(error)")
            return []
        }
    }
    
    // Check Relationship Status
    // Returns: "amigos", "pendiente", "siguiendo", "none"
    func checkRelationshipStatus(with targetId: UUID) async throws -> String {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { throw SupabaseError.missingTokens }
        
        // 1. Check if we are friends (in 'friends' table)
        let friendsUrl = URL(string: "\(supabaseURL)/rest/v1/friends?user_id=eq.\(currentUser.id)&friend_id=eq.\(targetId.uuidString)&status=eq.active&select=id")!
        var friendsReq = URLRequest(url: friendsUrl)
        friendsReq.cachePolicy = .reloadIgnoringLocalCacheData
        friendsReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        friendsReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (friendsData, friendsResp) = try await URLSession.shared.data(for: friendsReq)
        if let friendsHttp = friendsResp as? HTTPURLResponse, friendsHttp.statusCode == 200 {
            struct IdRow: Decodable { let id: UUID }
            if let rows = try? JSONDecoder().decode([IdRow].self, from: friendsData), !rows.isEmpty {
                return "amigos"
            }
        }
        
        // 2. Check if there's a pending friend request I sent
        let pendingUrl = URL(string: "\(supabaseURL)/rest/v1/friend_requests?sender_id=eq.\(currentUser.id)&receiver_id=eq.\(targetId.uuidString)&status=eq.pending&select=id")!
        var pendingReq = URLRequest(url: pendingUrl)
        pendingReq.cachePolicy = .reloadIgnoringLocalCacheData
        pendingReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        pendingReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (pendingData, pendingResp) = try await URLSession.shared.data(for: pendingReq)
        if let pendingHttp = pendingResp as? HTTPURLResponse, pendingHttp.statusCode == 200 {
            struct IdRow: Decodable { let id: UUID }
            if let rows = try? JSONDecoder().decode([IdRow].self, from: pendingData), !rows.isEmpty {
                return "pendiente"
            }
        }
        
        // 3. Check if I'm following them (in 'followers' table)
        let followingUrl = URL(string: "\(supabaseURL)/rest/v1/followers?follower_id=eq.\(currentUser.id)&followed_id=eq.\(targetId.uuidString)&select=id")!
        var followingReq = URLRequest(url: followingUrl)
        followingReq.cachePolicy = .reloadIgnoringLocalCacheData
        followingReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        followingReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (followingData, followingResp) = try await URLSession.shared.data(for: followingReq)
        if let followingHttp = followingResp as? HTTPURLResponse, followingHttp.statusCode == 200 {
            struct IdRow: Decodable { let id: UUID }
            if let rows = try? JSONDecoder().decode([IdRow].self, from: followingData), !rows.isEmpty {
                return "siguiendo"
            }
        }
        
        return "none"
    }
    
    // MARK: - Posts & Comments
    
    func fetchPosts(limit: Int = 20) async throws -> [PostDTO] {
        var accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        
        // Helper to perform request
        func performRequest(token: String) async throws -> (Data, HTTPURLResponse) {
            // 1. Try Complex Query (Updated for detailed reactions with user info)
            let complexUrlString = "\(supabaseURL)/rest/v1/posts?select=*,creator:users(username,nombre,apellido,avatar_url),post_reactions(id,post_id,user_id,emoji,user:users(username,nombre,apellido,avatar_url)),comments(*,user:users(username,nombre,apellido,avatar_url))&order=created_at.desc&limit=\(limit)"
            
            if let url = URL(string: complexUrlString) {
                var request = URLRequest(url: url)
                request.httpMethod = "GET"
                request.cachePolicy = .reloadIgnoringLocalCacheData // FORCE FRESH DATA
                request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
                request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
                
                // Intentar request compleja
                let (data, response) = try await URLSession.shared.data(for: request)
                if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                    return (data, httpResponse)
                }
                
                 // Si falla con error no-200 (que no sea 406/etc si fuera error de esquema), revisamos si es 401
                 if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 401 {
                     return (data, httpResponse)
                 }
            }
            
            // 2. Fallback: Simple Query
            let simpleUrlString = "\(supabaseURL)/rest/v1/posts?select=*,creator:users(username,nombre,apellido,avatar_url)&order=created_at.desc&limit=\(limit)"
            guard let url = URL(string: simpleUrlString) else { throw SupabaseError.invalidURL }
            
            var request = URLRequest(url: url)
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
            request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                 throw SupabaseError.fetchUserFailed
            }
            return (data, httpResponse)
        }
        
        // First attempt
        var data: Data
        var response: HTTPURLResponse
        
        do {
            (data, response) = try await performRequest(token: accessToken)
        } catch {
             // Network Error: Fallback to Cache
             let nsError = error as NSError
             let networkErrors = [NSURLErrorNotConnectedToInternet, NSURLErrorNetworkConnectionLost, NSURLErrorTimedOut, NSURLErrorCannotConnectToHost]
             if networkErrors.contains(nsError.code) {
                 print("⚠️ Network error in fetchPosts. Attempting to load from cache...")
                 if let cachedPosts = CacheManager.shared.load("cached_posts.json", as: [PostDTO].self) {
                     return cachedPosts
                 }
             }
             throw error
        }
        
        // Check for 401 Unauthorized
        if response.statusCode == 401 {
            print("⚠️ 401 in fetchPosts, attempting refresh...")
            try await refreshSession()
            // Get new token
            accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
            // Retry
            (data, response) = try await performRequest(token: accessToken)
        }
        
        guard response.statusCode == 200 else {
             print("❌ fetchPosts failed with status: \(response.statusCode)")
             throw SupabaseError.fetchUserFailed
        }
        
        let posts = try decoder.decode([PostDTO].self, from: data)
        // Cache successful response
        CacheManager.shared.save(posts, to: "cached_posts.json")
        
        // Background pre-fetching of images
        Task {
            await prefetchImages(for: posts)
        }
        
        return posts
    }
    
    // Pre-download images to populate URLCache
    private func prefetchImages(for posts: [PostDTO]) async {
        print("🚀 Starting background image prefetch for \(posts.count) posts...")
        for post in posts {
            // 1. Post Media
            if let url = URL(string: post.media_url) {
                let request = URLRequest(url: url)
                // Only download if not already cached (URLSession handles this generally via cache policy, 
                // but explicit check or just dataTask is fine. Simplest is dataTask to trigger cache.)
                if URLCache.shared.cachedResponse(for: request) == nil {
                     _ = try? await URLSession.shared.data(for: request)
                }
            }
            // 2. Creator Avatar
            if let avatarUrl = post.creator?.avatar_url, let url = URL(string: avatarUrl) {
                let request = URLRequest(url: url)
                if URLCache.shared.cachedResponse(for: request) == nil {
                     _ = try? await URLSession.shared.data(for: request)
                }
            }
        }
        print("✅ Background image prefetch completed.")
    }
    
    // Create Post with Image Upload (Updated to support content_type)
    func createPost(caption: String, image: UIImage, isAnonymous: Bool = false, contentType: String = "post", title: String? = nil, category: String? = nil) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        // 1. Upload Image
        let imageId = UUID().uuidString
        let imagePath = "\(currentUser.id)/\(imageId).jpg"
        
        guard let imageData = image.jpegData(compressionQuality: 0.7) else {
            throw SupabaseError.uploadFailed
        }
        
        let storageUrl = "\(supabaseURL)/storage/v1/object/post_media/\(imagePath)"
        var uploadRequest = URLRequest(url: URL(string: storageUrl)!)
        uploadRequest.httpMethod = "POST"
        uploadRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        uploadRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        uploadRequest.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        uploadRequest.httpBody = imageData
        
        let (data, uploadResponse) = try await URLSession.shared.data(for: uploadRequest)
        
        if let httpResponse = uploadResponse as? HTTPURLResponse, httpResponse.statusCode != 200 {
            let errorString = String(data: data, encoding: .utf8) ?? "No response body"
            print("❌ Upload failed with status: \(httpResponse.statusCode)")
            print("❌ Response: \(errorString)")
             throw SupabaseError.uploadFailed
        }
        
        let mediaUrl = "\(supabaseURL)/storage/v1/object/public/post_media/\(imagePath)"
        
        // 2. Create Post Record
        struct NewPost: Encodable {
            let creator_id: UUID
            let caption: String
            let media_url: String
            let media_type: String
            let is_anonymous: Bool
            let content_type: String
            let title: String?
            let category: String?
        }
        
        let newPost = NewPost(
            creator_id: UUID(uuidString: currentUser.id)!,
            caption: caption,
            media_url: mediaUrl,
            media_type: "image",
            is_anonymous: isAnonymous,
            content_type: contentType,
            title: title,
            category: category
        )

        
        let postUrl = "\(supabaseURL)/rest/v1/posts"
        var postRequest = URLRequest(url: URL(string: postUrl)!)
        postRequest.httpMethod = "POST"
        postRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        postRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        postRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        postRequest.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        
        let encoder = JSONEncoder()
        postRequest.httpBody = try encoder.encode(newPost)
        
        let (_, postResponse) = try await URLSession.shared.data(for: postRequest)
        
        if let httpPostResponse = postResponse as? HTTPURLResponse, httpPostResponse.statusCode != 201 {
             throw SupabaseError.saveFailed
        }
    }
    
    // Create Post/Reel with Video Upload
    func createVideoPost(caption: String, videoURL: URL, thumbnail: UIImage?, isAnonymous: Bool = false, contentType: String = "both", durationSeconds: Int? = nil, title: String? = nil, category: String? = nil) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let videoId = UUID().uuidString
        
        // 1. Upload Video
        let videoPath = "\(currentUser.id)/\(videoId).mp4"
        let videoData = try Data(contentsOf: videoURL)
        
        let videoStorageUrl = "\(supabaseURL)/storage/v1/object/reel_videos/\(videoPath)"
        var videoUploadRequest = URLRequest(url: URL(string: videoStorageUrl)!)
        videoUploadRequest.httpMethod = "POST"
        videoUploadRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        videoUploadRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        videoUploadRequest.setValue("video/mp4", forHTTPHeaderField: "Content-Type")
        videoUploadRequest.httpBody = videoData
        
        let (responseData, videoResponse) = try await URLSession.shared.data(for: videoUploadRequest)
        
        guard let httpResponse = videoResponse as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            let errorString = String(data: responseData, encoding: .utf8) ?? "No response body"
            print("❌ Video upload failed: \(errorString)")
            throw SupabaseError.uploadFailed
        }
        
        let videoUrl = "\(supabaseURL)/storage/v1/object/public/reel_videos/\(videoPath)"
        
        // 2. Upload Thumbnail (if provided)
        var thumbnailUrl: String? = nil
        if let thumbnail = thumbnail, let thumbnailData = thumbnail.jpegData(compressionQuality: 0.7) {
            let thumbnailPath = "\(currentUser.id)/\(videoId)_thumb.jpg"
            let thumbnailStorageUrl = "\(supabaseURL)/storage/v1/object/video_thumbnails/\(thumbnailPath)"
            
            var thumbRequest = URLRequest(url: URL(string: thumbnailStorageUrl)!)
            thumbRequest.httpMethod = "POST"
            thumbRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            thumbRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            thumbRequest.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
            thumbRequest.httpBody = thumbnailData
            
            let (_, thumbResponse) = try await URLSession.shared.data(for: thumbRequest)
            
            if let httpThumbResponse = thumbResponse as? HTTPURLResponse, httpThumbResponse.statusCode == 200 {
                thumbnailUrl = "\(supabaseURL)/storage/v1/object/public/video_thumbnails/\(thumbnailPath)"
            }
        }
        
        // 3. Create Post Record
        struct NewVideoPost: Encodable {
            let creator_id: UUID
            let caption: String
            let media_url: String
            let media_type: String
            let is_anonymous: Bool
            let content_type: String
            let duration_seconds: Int?
            let thumbnail_url: String?
            let title: String?
            let category: String?
        }
        
        let newPost = NewVideoPost(
            creator_id: UUID(uuidString: currentUser.id)!,
            caption: caption,
            media_url: videoUrl,
            media_type: "video",
            is_anonymous: isAnonymous,
            content_type: contentType,
            duration_seconds: durationSeconds,
            thumbnail_url: thumbnailUrl,
            title: title,
            category: category
        )
        
        let postUrl = "\(supabaseURL)/rest/v1/posts"
        var postRequest = URLRequest(url: URL(string: postUrl)!)
        postRequest.httpMethod = "POST"
        postRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        postRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        postRequest.setValue("application/json", forHTTPHeaderField: "Content-Type")
        postRequest.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        
        let encoder = JSONEncoder()
        postRequest.httpBody = try encoder.encode(newPost)
        
        let (_, postResponse) = try await URLSession.shared.data(for: postRequest)
        
        guard let httpPostResponse = postResponse as? HTTPURLResponse, httpPostResponse.statusCode == 201 else {
            throw SupabaseError.saveFailed
        }
    }
    
    // Fetch Reels (videos con content_type "reel" o "both")
    func fetchReels(limit: Int = 20) async throws -> [PostDTO] {
        var accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        
        // Query para obtener solo reels (videos con content_type 'reel' o 'both')
        let urlString = "\(supabaseURL)/rest/v1/posts?select=*,creator:users(username,nombre,apellido,avatar_url),post_reactions(id,post_id,user_id,emoji,user:users(username,nombre,apellido,avatar_url)),comments(*,user:users(username,nombre,apellido,avatar_url))&media_type=eq.video&or=(content_type.eq.reel,content_type.eq.both)&order=created_at.desc&limit=\(limit)"
        
        print("🎬 [FETCH_REELS] Query URL: \(urlString)")
        
        guard let url = URL(string: urlString) else {
            throw SupabaseError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.cachePolicy = .reloadIgnoringLocalCacheData // FORCE FRESH DATA
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw SupabaseError.fetchUserFailed
        }
        
        print("📥 [FETCH_REELS] Status: \(httpResponse.statusCode)")
        
        if httpResponse.statusCode == 401 {
            print("⚠️ 401 in fetchReels, attempting refresh...")
            try await refreshSession()
            accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") ?? ""
            
            var retryRequest = URLRequest(url: url)
            retryRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            retryRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            
            let (retryData, retryResponse) = try await URLSession.shared.data(for: retryRequest)
            guard let retryHttpResponse = retryResponse as? HTTPURLResponse, retryHttpResponse.statusCode == 200 else {
                throw SupabaseError.fetchUserFailed
            }
            
            let reels = try decoder.decode([PostDTO].self, from: retryData)
            print("✅ [FETCH_REELS] Encontrados \(reels.count) reels")
            return reels
        }
        
        guard httpResponse.statusCode == 200 else {
            let errorString = String(data: data, encoding: .utf8) ?? "No details"
            print("❌ [FETCH_REELS] Error: \(errorString)")
            throw SupabaseError.fetchUserFailed
        }
        
        let reels = try decoder.decode([PostDTO].self, from: data)
        print("✅ [FETCH_REELS] Encontrados \(reels.count) reels")
        return reels
    }

    
    // Fetch Comments
    func fetchComments(for postId: String) async throws -> [CommentDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { throw SupabaseError.missingTokens }
        
        // Query: select=*,user:users(*)
        let urlString = "\(supabaseURL)/rest/v1/comments?post_id=eq.\(postId)&select=*,user:users(username,nombre,apellido,avatar_url)&order=created_at.asc"
        
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
             throw SupabaseError.fetchUserFailed
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([CommentDTO].self, from: data)
    }
    
    // MARK: - Reactions
    func reactToPost(postId: String, emoji: String) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        struct NewReaction: Encodable {
            let post_id: UUID
            let user_id: UUID
            let emoji: String
        }
        
        let newReaction = NewReaction(post_id: UUID(uuidString: postId)!, user_id: UUID(uuidString: currentUser.id)!, emoji: emoji)
        
        // Use upsert: on_conflict matching the unique constraint we will add in SQL
        let urlString = "\(supabaseURL)/rest/v1/post_reactions?on_conflict=post_id,user_id"
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        // Prefer: resolution=merge-duplicates updates existing row if conflict occurs
        request.setValue("resolution=merge-duplicates", forHTTPHeaderField: "Prefer") 
        
        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(newReaction)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             throw SupabaseError.saveFailed
        }
        
        try? await logInteraction(targetId: UUID(uuidString: postId)!, targetType: "post", interactionType: "reaction_emoji", metadata: ["emoji": emoji])
    }

    func removeReaction(postId: String) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let urlString = "\(supabaseURL)/rest/v1/post_reactions?post_id=eq.\(postId)&user_id=eq.\(currentUser.id)"
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             throw SupabaseError.saveFailed
        }
    }
    
    // Fetch aggregated reactions (simplified: just count them or fetch all?)
    // Real production app would use a view or rpc. We'll fetch all for now (careful with scale)
    // Or just rely on real-time count.
    
    func fetchPostReactions(postId: String) async throws -> [ReactionDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else { throw SupabaseError.missingTokens }
        
        let urlString = "\(supabaseURL)/rest/v1/post_reactions?post_id=eq.\(postId)&select=*"
        guard let url = URL(string: urlString) else { throw SupabaseError.invalidURL }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
             return []
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return try decoder.decode([ReactionDTO].self, from: data)
    }
    
    // Add Comment
    func addComment(postId: String, content: String) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        struct NewComment: Encodable {
            let post_id: UUID
            let user_id: UUID
            let content: String
        }
        
        let newComment = NewComment(post_id: UUID(uuidString: postId)!, user_id: UUID(uuidString: currentUser.id)!, content: content)

        
        let urlString = "\(supabaseURL)/rest/v1/comments"
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        
        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(newComment)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 201 {
             throw SupabaseError.saveFailed
        }
    }
    
    // MARK: - User Profile Functions
    
    // Fetch posts by specific user
    func fetchUserPosts(userId: UUID, limit: Int = 30) async throws -> [PostDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            print("❌ [FETCH_USER_POSTS] Missing access token")
            throw SupabaseError.missingTokens
        }
        
        // Primero intentamos con la query completa (incluyendo reactions y comments)
        let complexUrlString = "\(supabaseURL)/rest/v1/posts?creator_id=eq.\(userId.uuidString)&select=*,creator:users(username,nombre,apellido,avatar_url),post_reactions(id,post_id,user_id,emoji,user:users(username,nombre,apellido,avatar_url)),comments(*,user:users(username,nombre,apellido,avatar_url))&order=created_at.desc&limit=\(limit)"
        
        print("🔍 [FETCH_USER_POSTS] UserID: \(userId.uuidString)")
        print("🔍 [FETCH_USER_POSTS] Query URL: \(complexUrlString)")
        
        guard let url = URL(string: complexUrlString) else {
            print("❌ [FETCH_USER_POSTS] Invalid URL")
            throw SupabaseError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            print("❌ [FETCH_USER_POSTS] No HTTP response")
            throw SupabaseError.fetchUserFailed
        }
        
        print("📥 [FETCH_USER_POSTS] Status: \(httpResponse.statusCode)")
        
        // Log raw response for debugging
        if let jsonString = String(data: data, encoding: .utf8) {
            print("📦 [FETCH_USER_POSTS] Response: \(jsonString.prefix(500))...")
        }
        
        if httpResponse.statusCode != 200 {
            let errorString = String(data: data, encoding: .utf8) ?? "No details"
            print("❌ [FETCH_USER_POSTS] Error Response: \(errorString)")
            
            // Si la query compleja falla, intentar query simple
            print("⚠️ [FETCH_USER_POSTS] Intentando query simple...")
            let simpleUrlString = "\(supabaseURL)/rest/v1/posts?creator_id=eq.\(userId.uuidString)&select=*,creator:users(username,nombre,apellido,avatar_url)&order=created_at.desc&limit=\(limit)"
            
            guard let simpleUrl = URL(string: simpleUrlString) else {
                throw SupabaseError.invalidURL
            }
            
            var simpleRequest = URLRequest(url: simpleUrl)
            simpleRequest.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
            simpleRequest.setValue(supabaseKey, forHTTPHeaderField: "apikey")
            
            let (simpleData, simpleResponse) = try await URLSession.shared.data(for: simpleRequest)
            guard let simpleHttpResponse = simpleResponse as? HTTPURLResponse else {
                throw SupabaseError.fetchUserFailed
            }
            
            print("📥 [FETCH_USER_POSTS] Simple Query Status: \(simpleHttpResponse.statusCode)")
            
            if simpleHttpResponse.statusCode != 200 {
                let simpleErrorString = String(data: simpleData, encoding: .utf8) ?? "No details"
                print("❌ [FETCH_USER_POSTS] Simple Query Error: \(simpleErrorString)")
                throw SupabaseError.fetchUserFailed
            }
            
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601
            
            let posts = try decoder.decode([PostDTO].self, from: simpleData)
            print("✅ [FETCH_USER_POSTS] Encontrados \(posts.count) posts del usuario (query simple)")
            return posts
        }
        
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        
        do {
            let posts = try decoder.decode([PostDTO].self, from: data)
            print("✅ [FETCH_USER_POSTS] Encontrados \(posts.count) posts del usuario")
            return posts
        } catch {
            print("❌ [FETCH_USER_POSTS] Decoding error: \(error)")
            throw error
        }
    }
    
    // Count user stats (posts, followers, following, friends)
    func countUserStats(userId: UUID) async throws -> (posts: Int, followers: Int, following: Int, friends: Int) {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        // Count posts
        let postsUrl = URL(string: "\(supabaseURL)/rest/v1/posts?creator_id=eq.\(userId.uuidString)&select=id")!
        var postsReq = URLRequest(url: postsUrl)
        postsReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        postsReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        postsReq.setValue("count=exact", forHTTPHeaderField: "Prefer")
        
        // Count followers (people following this user -> followers table where followed_id = user)
        let followersUrl = URL(string: "\(supabaseURL)/rest/v1/followers?followed_id=eq.\(userId.uuidString)&select=id")!
        var followersReq = URLRequest(url: followersUrl)
        followersReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        followersReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        followersReq.setValue("count=exact", forHTTPHeaderField: "Prefer")
        followersReq.httpMethod = "GET" // Explicit GET
        
        // Count following (users this person follows -> followers table where follower_id = user)
        let followingUrl = URL(string: "\(supabaseURL)/rest/v1/followers?follower_id=eq.\(userId.uuidString)&select=id")!
        var followingReq = URLRequest(url: followingUrl)
        followingReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        followingReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        followingReq.setValue("count=exact", forHTTPHeaderField: "Prefer")
        followingReq.httpMethod = "GET"

        // Count friends (friends table where user_id = user AND status = active)
        let friendsUrl = URL(string: "\(supabaseURL)/rest/v1/friends?user_id=eq.\(userId.uuidString)&status=eq.active&select=id")!
        var friendsReq = URLRequest(url: friendsUrl)
        friendsReq.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        friendsReq.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        friendsReq.setValue("count=exact", forHTTPHeaderField: "Prefer")
        friendsReq.httpMethod = "GET"
        
        let finalPostsReq = postsReq
        let finalFollowersReq = followersReq
        let finalFollowingReq = followingReq
        let finalFriendsReq = friendsReq
        
        // Execute in parallel
        async let postsData = URLSession.shared.data(for: finalPostsReq)
        async let followersData = URLSession.shared.data(for: finalFollowersReq)
        async let followingData = URLSession.shared.data(for: finalFollowingReq)
        async let friendsData = URLSession.shared.data(for: finalFriendsReq) // Single request now
        
        let (_, postsResp) = try await postsData
        let (_, followersResp) = try await followersData
        let (_, followingResp) = try await followingData
        let (_, friendsResp) = try await friendsData
        
        let postsCount = (postsResp as? HTTPURLResponse)?.extractCount() ?? 0
        let followersCount = (followersResp as? HTTPURLResponse)?.extractCount() ?? 0
        let followingCount = (followingResp as? HTTPURLResponse)?.extractCount() ?? 0
        let friendsCount = (friendsResp as? HTTPURLResponse)?.extractCount() ?? 0
        
        return (postsCount, followersCount, followingCount, friendsCount)
        

    }
    
    // Delete Post
    func deletePost(postId: String) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let urlString = "\(supabaseURL)/rest/v1/posts?id=eq.\(postId)"
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             throw SupabaseError.saveFailed
        }
    }

    // Update Post
    func updatePost(postId: String, caption: String, title: String? = nil, category: String? = nil) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let urlString = "\(supabaseURL)/rest/v1/posts?id=eq.\(postId)"
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "PATCH"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        var body: [String: Any] = ["caption": caption]
        if let title = title { body["title"] = title }
        if let category = category { body["category"] = category }
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             throw SupabaseError.saveFailed
        }
    }

    // MARK: - Groups Methods
    
    func createGroup(name: String, description: String?, type: String, fileURL: URL?) async throws -> GroupDTO {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { throw SupabaseError.missingTokens }
        
        // 1. Upload Image logic skipped for brevity, user needs 'groups' bucket or similar.
        
        let url = URL(string: "\(supabaseURL)/rest/v1/groups")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=representation", forHTTPHeaderField: "Prefer")
        
        let body: [String: Any] = [
            "name": name,
            "description": description ?? "",
            "type": type,
            "creator_id": currentUser.id
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
             throw SupabaseError.uploadFailed 
        }
        
        let groups = try JSONDecoder().decode([GroupDTO].self, from: data)
        guard let group = groups.first else { throw SupabaseError.uploadFailed }
        
        // 2. Add creator as admin
        try await joinGroup(groupId: group.id, role: "admin")
        
        return group
    }
    
    func joinGroup(groupId: UUID, role: String = "member") async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { throw SupabaseError.missingTokens }
              
        let url = URL(string: "\(supabaseURL)/rest/v1/group_members")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "group_id": groupId.uuidString,
            "user_id": currentUser.id,
            "role": role
        ]
        
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw SupabaseError.uploadFailed
        }
    }
    
    func fetchMyGroups() async throws -> [GroupDTO] {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else { throw SupabaseError.missingTokens }

        // Fetch groups where user is a member
        let url = URL(string: "\(supabaseURL)/rest/v1/group_members?user_id=eq.\(currentUser.id)&select=group:groups!group_members_group_id_fkey(*)")!
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else { return [] }
        
        struct MemberRow: Decodable { let group: GroupDTO }
        let rows = try JSONDecoder().decode([MemberRow].self, from: data)
        return rows.map { $0.group }
    }

    func uploadAvatar(userId: String, data: Data) async throws -> String {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let fileName = "\(userId)/avatar_\(Int(Date().timeIntervalSince1970)).jpg"
        let url = URL(string: "\(supabaseURL)/storage/v1/object/avatars/\(fileName)")!
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        request.httpBody = data
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.fetchUserFailed
        }
        
        // Retornar URL pública
        return "\(supabaseURL)/storage/v1/object/public/avatars/\(fileName)"
    }
    
    // MARK: - Banner Functions
    
    // Upload Banner
    func uploadBanner(_ image: UIImage) async throws -> String {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        guard let imageData = image.jpegData(compressionQuality: 0.7) else {
            throw SupabaseError.saveFailed
        }
        
        let timestamp = Int(Date().timeIntervalSince1970)
        let fileName = "\(currentUser.id)/banner_\(timestamp).jpg"
        
        let uploadURL = URL(string: "\(supabaseURL)/storage/v1/object/banners/\(fileName)")!
        var request = URLRequest(url: uploadURL)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("image/jpeg", forHTTPHeaderField: "Content-Type")
        request.httpBody = imageData
        
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.saveFailed
        }
        
        let publicURL = "\(supabaseURL)/storage/v1/object/public/banners/\(fileName)"
        try await updateUserBanner(url: publicURL)
        
        return publicURL
    }
    
    // Delete Banner
    func deleteBanner() async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        guard let profile = try? await fetchCurrentUserProfile(),
              let bannerUrl = profile.bannerUrl,
              let fileName = bannerUrl.split(separator: "/").last else {
            return
        }
        
        let path = "\(currentUser.id)/\(fileName)"
        let deleteURL = URL(string: "\(supabaseURL)/storage/v1/object/banners/\(path)")!
        var request = URLRequest(url: deleteURL)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.saveFailed
        }
        
        try await updateUserBanner(url: nil)
    }
    
    // Update User Banner URL
    private func updateUserBanner(url: String?) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let updateURL = URL(string: "\(supabaseURL)/rest/v1/users?id=eq.\(currentUser.id)")!
        var request = URLRequest(url: updateURL)
        request.httpMethod = "PATCH"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any?] = ["banner_url": url]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.saveFailed
        }
    }

    
    func updateUserProfile(id: String, profile: ProfileDTO) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/users")!
        var request = URLRequest(url: url)
        request.httpMethod = "POST" 
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("return=minimal", forHTTPHeaderField: "Prefer")
        // Upsert header
        request.setValue("resolution=merge-duplicates", forHTTPHeaderField: "Prefer")
        
        let encoder = JSONEncoder()
        request.httpBody = try encoder.encode(profile)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.fetchUserFailed
        }
    }
    
    func fetchCurrentUserProfile() async throws -> ProfileDTO? {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token"),
              let currentUser = loadSession()?.user else {
             return nil
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/users?id=eq.\(currentUser.id)&select=*")!
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
            print("❌ Error fetching user profile. Status: \((response as? HTTPURLResponse)?.statusCode ?? 0)")
            if let str = String(data: data, encoding: .utf8) { print("Response: \(str)") }
            return nil
        }
        
        let decoder = JSONDecoder()
        let profiles = try decoder.decode([ProfileDTO].self, from: data)
        return profiles.first
    }
}

extension HTTPURLResponse {
    func extractCount() -> Int {
        guard let range = self.value(forHTTPHeaderField: "Content-Range") else { return 0 }
        // range format: "0-0/5" or "*/5"
        return Int(range.split(separator: "/").last ?? "") ?? 0
    }
}


// MARK: - Models
struct AuthSession: Codable {
    let accessToken: String
    let refreshToken: String
    let user: User
}

struct SessionResponse {
    let providerToken: String?
    let providerRefreshToken: String?
}

struct User: Codable, Equatable {
    let id: String
    let email: String?
    let userMetadata: UserMetadata?
    
    enum CodingKeys: String, CodingKey {
        case id
        case email
        case userMetadata = "user_metadata"
    }
    
    static func == (lhs: User, rhs: User) -> Bool {
        return lhs.id == rhs.id && lhs.email == rhs.email && lhs.userMetadata == rhs.userMetadata
    }
}

struct UserMetadata: Codable, Equatable {
    let avatarUrl: String?
    let fullName: String?
    let name: String?
    
    enum CodingKeys: String, CodingKey {
        case avatarUrl = "avatar_url"
        case fullName = "full_name"
        case name
    }
}

// MARK: - Errors
enum SupabaseError: Error, LocalizedError {
    case invalidURL
    case invalidCallback
    case missingTokens
    case fetchUserFailed
    case uploadFailed
    case saveFailed
    case foreignKeyViolation
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "URL inválida"
        case .invalidCallback:
            return "Callback de autenticación inválido"
        case .missingTokens:
            return "Tokens de autenticación no encontrados"
        case .fetchUserFailed:
            return "Error al obtener información del usuario"
        case .uploadFailed:
            return "Error al subir la imagen"
        case .saveFailed:
            return "Error al guardar el registro"
        case .foreignKeyViolation:
            return "Error de referencia: El mensaje original ya no existe"
        }
    }
}



// MARK: - DTOs
struct ProfileDTO: Codable, Identifiable {
    let id: UUID
    let username: String?
    let nombre: String?
    let apellido: String?
    let avatarUrl: String?
    let bannerUrl: String?
    // Added fields from migration just in case we need them later
    let bio: String?
    let occupation: String? // Nueva propiedad: Ocupación/Profesión
    let country: String?
    let edad: Int?
    let altura: Int?
    let peso: Int?
    let estadoCivil: String?
    let estadoRegion: String?
    // User presence fields
    let isActive: Bool?
    let lastActiveAt: String?
    
    enum CodingKeys: String, CodingKey {
        case id
        case username
        case nombre
        case apellido
        case avatarUrl = "avatar_url"
        case bannerUrl = "banner_url"
        case bio
        case occupation // Mismo nombre en DB
        case country = "pais"
        case edad
        case altura = "altura_cm"
        case peso = "peso_kg"
        case estadoCivil = "estado_civil"
        case estadoRegion = "estado_region"
        case isActive = "is_active"
        case lastActiveAt = "last_active_at"
    }
}

struct MessageDTO: Codable, Identifiable {
    let id: Int
    let senderId: UUID
    let receiverId: UUID
    let content: String
    let isTemporary: Bool?
    let seenAt: String?
    let createdAt: Date
    let status: String?
    let replyToId: Int?
    
    // Snapshot Columns for Robust Threading
    let replyContextContent: String?
    let replyContextSenderUsername: String?
    
    enum CodingKeys: String, CodingKey {
        case id
        case senderId = "sender_id"
        case receiverId = "receiver_id"
        case content
        case isTemporary = "is_temporary"
        case seenAt = "seen_at"
        case createdAt = "created_at"
        case status
        case replyToId = "reply_to_id"
        case sender
        case receiver
        case replyToMessage = "reply_to_message"
        case replyContextContent = "reply_context_content"
        case replyContextSenderUsername = "reply_context_sender_username"
    }
    
    let sender: PostCreator?
    let receiver: PostCreator?
    let replyToMessage: BoxedMessageDTO?
}

// Helper for recursive decoding (Reference Type to break recursion cycle)
final class BoxedMessageDTO: Codable {
    let message: MessageDTO
    
    init(message: MessageDTO) {
        self.message = message
    }
    
    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        self.message = try container.decode(MessageDTO.self)
    }
    
    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(message)
    }
}

// MARK: - Direct Message DTO (WhatsApp-style)
struct DirectMessageDTO: Codable, Identifiable {
    let id: Int64
    let sender_id: UUID
    let receiver_id: UUID
    let content: String
    let is_read: Bool
    let created_at: Date
    let is_temporary: Bool?
    let seen_at: Date?
    let status: String?
    let delivered_at: Date?
    
    var isFromCurrentUser: Bool {
        guard let currentId = SupabaseClient.shared.loadSession()?.user.id else { return false }
        return sender_id.uuidString.lowercased() == currentId.lowercased()
    }
}

// MARK: - Post DTOs
struct PostDTO: Identifiable, Codable {
    let id: UUID
    let creator_id: UUID
    let media_url: String
    let media_type: String // "image" o "video"
    let caption: String?
    let likes_count: Int // Legacy, might be unused if we switch to count
    let created_at: Date
    let creator: PostCreator?
    let is_anonymous: Bool?
    let post_reactions: [ReactionDTO]?
    let comments: [CommentDTO]?
    let content_type: String? // "post", "reel", o "both"
    let duration_seconds: Int? // Duración del video en segundos (null para imágenes)
    let thumbnail_url: String? // URL del thumbnail/preview del video
    let title: String?
    let category: String?
    
    var reactionCount: Int {
        return post_reactions?.count ?? 0
    }
    
    var isVideo: Bool {
        return media_type == "video"
    }
    
    var isReel: Bool {
        return content_type == "reel" || content_type == "both"
    }
    
    var isPost: Bool {
        return content_type == "post" || content_type == "both"
    }
}

struct ReactionCountDTO: Codable {
    let count: Int
}

struct ReactionDTO: Identifiable, Codable {
    let id: UUID
    let post_id: UUID
    let user_id: UUID
    let emoji: String
    let user: PostCreator?
}

struct PostCreator: Codable {
    let username: String?
    let nombre: String?
    let apellido: String?
    let avatar_url: String?
    
    func toProfileDTO(id: UUID) -> ProfileDTO {
        return ProfileDTO(
            id: id,
            username: username,
            nombre: nombre,
            apellido: apellido,
            avatarUrl: avatar_url,
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
    }
}

struct CommentDTO: Identifiable, Codable {
    let id: UUID
    let post_id: UUID
    let user_id: UUID
    let content: String
    let created_at: Date
    let user: PostCreator? // Reuse creator struct for basic user info
    let parent_id: UUID?
    let likes_count: Int?
    let dislikes_count: Int?
    var replies: [CommentDTO]? // For UI nesting
}




extension SupabaseClient {
    // MARK: - Advanced Comments & Interactions
    
    func reactToComment(commentId: UUID, type: String) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        struct ReactionPayload: Encodable {
            let comment_id: UUID
            let user_id: UUID
            let reaction_type: String
        }
        
        let payload = ReactionPayload(comment_id: commentId, user_id: UUID(uuidString: currentUser.id)!, reaction_type: type)
        
        // Upsert reaction
        let url = "\(supabaseURL)/rest/v1/comment_reactions"
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("resolution=merge-duplicates", forHTTPHeaderField: "Prefer")
        
        request.httpBody = try JSONEncoder().encode(payload)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.uploadFailed
        }
        
        // Log interaction
        try? await logInteraction(targetId: commentId, targetType: "comment", interactionType: type)
    }
    
    func removeCommentReaction(commentId: UUID) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let userId = currentUser.id
        let urlString = "\(supabaseURL)/rest/v1/comment_reactions?comment_id=eq.\(commentId.uuidString)&user_id=eq.\(userId)"
        
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.saveFailed
        }
    }
    
    /// Returns the user's reaction type ("like" or "dislike") for a comment, or nil if none
    func fetchMyCommentReaction(commentId: UUID) async throws -> String? {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let userId = currentUser.id
        let urlString = "\(supabaseURL)/rest/v1/comment_reactions?comment_id=eq.\(commentId.uuidString)&user_id=eq.\(userId)&select=reaction_type"
        
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "GET"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
            throw SupabaseError.saveFailed
        }
        
        struct ReactionResult: Decodable {
            let reaction_type: String
        }
        
        let results = try JSONDecoder().decode([ReactionResult].self, from: data)
        return results.first?.reaction_type
    }
    
    // Delete Post
    func deletePost(id: UUID) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/posts?id=eq.\(id.uuidString)")!
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
             throw SupabaseError.saveFailed
        }
    }
    
    // Update Post
    func updatePost(id: UUID, title: String?, caption: String?, category: String?) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        var updates: [String: Any] = [:]
        if let t = title { updates["title"] = t }
        if let c = caption { updates["caption"] = c }
        if let cat = category { updates["category"] = cat }
        
        guard !updates.isEmpty else { return }
        
        let url = URL(string: "\(supabaseURL)/rest/v1/posts?id=eq.\(id.uuidString)")!
        var request = URLRequest(url: url)
        request.httpMethod = "PATCH"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        request.httpBody = try JSONSerialization.data(withJSONObject: updates)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
             throw SupabaseError.saveFailed
        }
    }
    
    func postComment(postId: UUID, content: String, parentId: UUID? = nil) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        struct NewComment: Encodable {
            let post_id: UUID
            let user_id: UUID
            let content: String
            let parent_id: UUID?
        }
        
        let newComment = NewComment(
            post_id: postId,
            user_id: UUID(uuidString: currentUser.id)!,
            content: content,
            parent_id: parentId
        )
        
        let url = "\(supabaseURL)/rest/v1/comments"
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        request.httpBody = try JSONEncoder().encode(newComment)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) else {
             throw SupabaseError.uploadFailed
        }
        
        // Log interaction
         try? await logInteraction(targetId: postId, targetType: "post", interactionType: "comment")
    }
    
    func deleteComment(commentId: String) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let urlString = "\(supabaseURL)/rest/v1/comments?id=eq.\(commentId)"
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             throw SupabaseError.saveFailed
        }
    }
    
    func updateComment(commentId: String, content: String) async throws {
        guard let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            throw SupabaseError.missingTokens
        }
        
        let urlString = "\(supabaseURL)/rest/v1/comments?id=eq.\(commentId)"
        var request = URLRequest(url: URL(string: urlString)!)
        request.httpMethod = "PATCH"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body = ["content": content]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        if let httpResponse = response as? HTTPURLResponse, !(200...299).contains(httpResponse.statusCode) {
             throw SupabaseError.saveFailed
        }
    }
    
    func logInteraction(targetId: UUID, targetType: String, interactionType: String, metadata: [String: String]? = nil) async throws {
        guard let currentUser = loadSession()?.user,
              let accessToken = UserDefaults.standard.string(forKey: "supabase_access_token") else {
            return
        }
        
        struct InteractionLog: Encodable {
            let user_id: UUID
            let target_id: UUID
            let target_type: String
            let interaction_type: String
            let metadata: [String: String]?
        }
        
        let log = InteractionLog(
            user_id: UUID(uuidString: currentUser.id)!,
            target_id: targetId,
            target_type: targetType,
            interaction_type: interactionType,
            metadata: metadata
        )
        
        let url = "\(supabaseURL)/rest/v1/user_interactions"
        var request = URLRequest(url: URL(string: url)!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue(supabaseKey, forHTTPHeaderField: "apikey")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        request.httpBody = try JSONEncoder().encode(log)
        
        _ = try? await URLSession.shared.data(for: request)
    }
}


