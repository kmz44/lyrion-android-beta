//
//  CalendarManager.swift
//  Lyrion
//
//

import Foundation
import EventKit

class CalendarManager: ObservableObject {
    static let shared = CalendarManager()
    
    private let eventStore = EKEventStore()
    @Published var hasCalendarAccess = false
    @Published var events: [CalendarEvent] = []
    
    private init() {
        checkCalendarAccess()
    }
    
    // MARK: - Request Access
    func requestCalendarAccess() async -> Bool {
        do {
            let granted = try await eventStore.requestFullAccessToEvents()
            await MainActor.run {
                self.hasCalendarAccess = granted
            }
            return granted
        } catch {
            print("Error requesting calendar access: \(error)")
            return false
        }
    }
    
    private func checkCalendarAccess() {
        let status = EKEventStore.authorizationStatus(for: .event)
        hasCalendarAccess = status == .fullAccess || status == .authorized
    }
    
    // MARK: - Fetch Events
    func fetchEvents(from startDate: Date, to endDate: Date) async -> [CalendarEvent] {
        guard hasCalendarAccess else {
            return []
        }
        
        // Ejecutar en background thread para evitar bloquear la UI
        return await Task.detached(priority: .userInitiated) { [eventStore] in
            let predicate = eventStore.predicateForEvents(withStart: startDate, end: endDate, calendars: nil)
            let ekEvents = eventStore.events(matching: predicate)
            
            let calendarEvents = ekEvents.map { event in
                CalendarEvent(
                    id: event.eventIdentifier,
                    title: event.title,
                    startDate: event.startDate,
                    endDate: event.endDate,
                    isAllDay: event.isAllDay,
                    calendar: event.calendar.title,
                    notes: event.notes
                )
            }
            
            return calendarEvents.sorted { $0.startDate < $1.startDate }
        }.value
    }
    
    // MARK: - Fetch Google Calendar Events
    func fetchGoogleCalendarEvents(accessToken: String, from startDate: Date, to endDate: Date) async throws -> [CalendarEvent] {
        let dateFormatter = ISO8601DateFormatter()
        
        let timeMin = dateFormatter.string(from: startDate)
        let timeMax = dateFormatter.string(from: endDate)
        
        let urlString = "https://www.googleapis.com/calendar/v3/calendars/primary/events?timeMin=\(timeMin)&timeMax=\(timeMax)&singleEvents=true&orderBy=startTime"
        
        guard let url = URL(string: urlString) else {
            throw CalendarError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse,
              httpResponse.statusCode == 200 else {
            throw CalendarError.fetchFailed
        }
        
        let decoder = JSONDecoder()
        let googleResponse = try decoder.decode(GoogleCalendarResponse.self, from: data)
        
        let service = GoogleCalendarService.shared
        let events = googleResponse.items.compactMap { item -> CalendarEvent? in
            guard let startDate = service.parseGoogleDate(item.start),
                  let endDate = service.parseGoogleDate(item.end) else {
                return nil
            }
            
            return CalendarEvent(
                id: item.id,
                title: item.summary ?? "Sin título",
                startDate: startDate,
                endDate: endDate,
                isAllDay: item.start.date != nil,
                calendar: "Google Calendar",
                notes: item.description
            )
        }
        
        return events
    }
}

// MARK: - Models
struct CalendarEvent: Identifiable, Codable {
    let id: String
    let title: String
    let startDate: Date
    let endDate: Date
    let isAllDay: Bool
    let calendar: String
    let notes: String?
}



enum CalendarError: Error {
    case invalidURL
    case fetchFailed
    case accessDenied
}
