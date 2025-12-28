//
//  GoogleCalendarService.swift
//  Lyrion
//

import Foundation

class GoogleCalendarService {
    static let shared = GoogleCalendarService()
    
    private let baseURL = "https://www.googleapis.com/calendar/v3"
    
    private init() {}
    
    // MARK: - Check Token
    func hasValidToken() -> String? {
        // Primero intentar obtener el provider_token (Google access token)
        if let googleToken = SupabaseClient.shared.getProviderToken() {
            print("🔐 Token recuperado: \(googleToken.prefix(30))...")
            print("🔐 Token length: \(googleToken.count) caracteres")
            return googleToken
        }
        
        print("❌ No se encontró provider_token en UserDefaults")
        // Si no hay provider_token, el usuario necesita re-autenticarse
        return nil
    }
    
    // MARK: - Fetch Events
    func fetchEvents(accessToken: String, from startDate: Date, to endDate: Date) async throws -> [CalendarEvent] {
        let dateFormatter = ISO8601DateFormatter()
        dateFormatter.timeZone = TimeZone(identifier: "UTC")
        
        let timeMin = dateFormatter.string(from: startDate)
        let timeMax = dateFormatter.string(from: endDate)
        
        // Agregar maxResults para obtener más eventos y showDeleted=false para excluir eventos eliminados
        let urlString = "\(baseURL)/calendars/primary/events?timeMin=\(timeMin)&timeMax=\(timeMax)&singleEvents=true&orderBy=startTime&maxResults=250&showDeleted=false"
        
        guard let url = URL(string: urlString) else {
            throw GoogleCalendarError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        
        print("📅 Fetching Google Calendar events...")
        print("🔗 URL: \(urlString)")
        print("🔑 Token: \(accessToken.prefix(20))...")
        print("📆 Rango: \(timeMin) a \(timeMax)")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            print("❌ Invalid HTTP response")
            throw GoogleCalendarError.invalidResponse
        }
        
        print("📊 HTTP Status: \(httpResponse.statusCode)")
        
        if httpResponse.statusCode == 401 {
            print("❌ Unauthorized - Token inválido o expirado")
            throw GoogleCalendarError.unauthorized
        }
        
        guard httpResponse.statusCode == 200 else {
            print("❌ Fetch failed with status: \(httpResponse.statusCode)")
            if let responseString = String(data: data, encoding: .utf8) {
                print("📄 Response body: \(responseString)")
            }
            throw GoogleCalendarError.fetchFailed
        }
        
        print("✅ Successfully fetched calendar data")
        
        // Debug: Ver qué está devolviendo Google
        if let responseString = String(data: data, encoding: .utf8) {
            print("📦 Response data: \(responseString)")
        }
        
        let decoder = JSONDecoder()
        do {
            let googleResponse = try decoder.decode(GoogleCalendarResponse.self, from: data)
            print("✅ Decoded \(googleResponse.items.count) events")
            
            return googleResponse.items.map { item in
                CalendarEvent(
                    id: item.id,
                    title: item.summary ?? "Sin título",
                    startDate: parseGoogleDate(item.start) ?? Date(),
                    endDate: parseGoogleDate(item.end) ?? Date(),
                    isAllDay: item.start.date != nil,
                    calendar: "Google Calendar",
                    notes: item.description
                )
            }
        } catch {
            print("❌ Error decodificando JSON: \(error)")
            if let decodingError = error as? DecodingError {
                switch decodingError {
                case .keyNotFound(let key, let context):
                    print("❌ Key '\(key.stringValue)' not found: \(context.debugDescription)")
                case .typeMismatch(let type, let context):
                    print("❌ Type mismatch for type \(type): \(context.debugDescription)")
                case .valueNotFound(let type, let context):
                    print("❌ Value not found for type \(type): \(context.debugDescription)")
                case .dataCorrupted(let context):
                    print("❌ Data corrupted: \(context.debugDescription)")
                @unknown default:
                    print("❌ Unknown decoding error")
                }
            }
            throw error
        }
    }
    
    // MARK: - Create Event
    func createEvent(accessToken: String, title: String, startDate: Date, endDate: Date, description: String?, isAllDay: Bool) async throws {
        let urlString = "\(baseURL)/calendars/primary/events"
        
        guard let url = URL(string: urlString) else {
            throw GoogleCalendarError.invalidURL
        }
        
        let dateFormatter = ISO8601DateFormatter()
        
        var eventData: [String: Any] = [
            "summary": title
        ]
        
        if isAllDay {
            let dateOnlyFormatter = DateFormatter()
            dateOnlyFormatter.dateFormat = "yyyy-MM-dd"
            eventData["start"] = ["date": dateOnlyFormatter.string(from: startDate)]
            eventData["end"] = ["date": dateOnlyFormatter.string(from: endDate)]
        } else {
            eventData["start"] = ["dateTime": dateFormatter.string(from: startDate)]
            eventData["end"] = ["dateTime": dateFormatter.string(from: endDate)]
        }
        
        if let description = description {
            eventData["description"] = description
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: eventData)
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw GoogleCalendarError.createFailed
        }
    }
    
    // MARK: - Delete Event
    func deleteEvent(accessToken: String, eventId: String) async throws {
        let urlString = "\(baseURL)/calendars/primary/events/\(eventId)"
        
        guard let url = URL(string: urlString) else {
            throw GoogleCalendarError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        
        let (_, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 204 else {
            throw GoogleCalendarError.deleteFailed
        }
    }
    
    // Helper para parsear fechas de Google
    func parseGoogleDate(_ googleDateTime: GoogleDateTime) -> Date? {
        if let dateTime = googleDateTime.dateTime {
            return ISO8601DateFormatter().date(from: dateTime)
        } else if let date = googleDateTime.date {
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyy-MM-dd"
            return formatter.date(from: date)
        }
        return nil
    }
}

// MARK: - Models
struct GoogleCalendarResponse: Codable {
    let items: [GoogleCalendarItem]
}

struct GoogleCalendarItem: Codable {
    let id: String
    let summary: String?
    let description: String?
    let start: GoogleDateTime
    let end: GoogleDateTime
}

struct GoogleDateTime: Codable {
    let dateTime: String?
    let date: String?
}

enum GoogleCalendarError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case unauthorized
    case fetchFailed
    case createFailed
    case deleteFailed
    
    var errorDescription: String? {
        switch self {
        case .invalidURL:
            return "URL inválida"
        case .invalidResponse:
            return "Respuesta inválida del servidor"
        case .unauthorized:
            return "No autorizado. Por favor, inicia sesión nuevamente"
        case .fetchFailed:
            return "Error al obtener datos"
        case .createFailed:
            return "Error al crear"
        case .deleteFailed:
            return "Error al eliminar"
        }
    }
}
