//
//  CacheManager.swift
//  Lyrion
//
//  Created for Offline Support.
//

import Foundation

class CacheManager {
    static let shared = CacheManager()
    private let fileManager = FileManager.default
    private let cacheDirectory: URL
    
    private init() {
        // Use Documents directory for persistent storage that survives restarts and system purge
        cacheDirectory = fileManager.urls(for: .documentDirectory, in: .userDomainMask).first!
    }
    
    /// Saves any Encodable data to a file in the caches directory
    func save<T: Encodable>(_ data: T, to filename: String) {
        let url = cacheDirectory.appendingPathComponent(filename)
        do {
            let encoder = JSONEncoder()
            // Ensure date formatting matches what we expect (though for raw storage standard is fine, 
            // but if we decode with specific strategy, we might want consistency. 
            // Default strategy is distinct from ISO8601 used in SupabaseClient, 
            // but as long as we use matching decoder it's fine. 
            // Actually, to match SupabaseClient's decoder behavior for caching DTOs which might have Dates,
            // we should probably use the same encoding strategy if the DTOs have Date properties.
            // PostDTO has `created_at: Date`. SupabaseClient uses `.iso8601` for decoding.
            // When encoding back to JSON for cache, we should use `.iso8601` to be safe if we reuse the same decoder.
            encoder.dateEncodingStrategy = .iso8601 
            
            let encoded = try encoder.encode(data)
            try encoded.write(to: url)
            print("✅ Cached data saved to \(filename)")
        } catch {
            print("❌ Error saving cache to \(filename): \(error)")
        }
    }
    
    /// Loads Decodable data from a file
    func load<T: Decodable>(_ filename: String, as type: T.Type) -> T? {
        let url = cacheDirectory.appendingPathComponent(filename)
        guard fileManager.fileExists(atPath: url.path) else { return nil }
        
        do {
            let data = try Data(contentsOf: url)
            let decoder = JSONDecoder()
            decoder.dateDecodingStrategy = .iso8601 // Match the encoder strategy
            let decoded = try decoder.decode(type, from: data)
            print("✅ Cached data loaded from \(filename)")
            return decoded
        } catch {
            print("❌ Error loading cache from \(filename): \(error)")
            // If cache is corrupted, maybe delete it?
            try? fileManager.removeItem(at: url)
            return nil
        }
    }
    
    /// Clears a specific cache file
    func clear(_ filename: String) {
        let url = cacheDirectory.appendingPathComponent(filename)
        try? fileManager.removeItem(at: url)
    }
}
