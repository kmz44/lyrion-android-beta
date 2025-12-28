//
//  HistoryView.swift
//  Lyrion
//

import SwiftUI
import SwiftData
#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

struct HistoryView: View {
    @EnvironmentObject var appManager: AppManager
    @EnvironmentObject var authManager: AuthManager
    @Environment(\.modelContext) var modelContext
    @Binding var currentThread: Thread?
    @FocusState.Binding var isPromptFocused: Bool
    @Binding var selectedTab: Int
    
    @Query(sort: \Thread.timestamp, order: .reverse) var threads: [Thread]
    @StateObject private var calendarManager = CalendarManager.shared
    
    @State private var selectedDate: Date = Date()
    @State private var iphoneEvents: [CalendarEvent] = []
    @State private var googleEvents: [CalendarEvent] = []
    @State private var googleTasks: [GoogleCalendarTask] = []
    @State private var showCalendarPermission = false
    @State private var showReauthAlert = false
    @State private var showCreateEventSheet = false
    @State private var showCreateTaskSheet = false
    @State private var googleCalendarError: String?
    @State private var viewMode: ViewMode = .day
    
    enum ViewMode: String, CaseIterable {
        case day = "Día"
        case week = "Semana"
        case month = "Mes"
    }
    
    private var systemGroupedBackground: Color {
        #if os(iOS)
        return Color(uiColor: .systemGroupedBackground)
        #elseif os(macOS)
        return Color(NSColor.controlBackgroundColor)
        #endif
    }
    
    private var secondarySystemGroupedBackground: Color {
        #if os(iOS)
        return Color(UIColor.secondarySystemGroupedBackground)
        #elseif os(macOS)
        return Color(NSColor.windowBackgroundColor)
        #endif
    }
    
    private var tertiarySystemGroupedBackground: Color {
        #if os(iOS)
        return Color(UIColor.tertiarySystemGroupedBackground)
        #elseif os(macOS)
        return Color(NSColor.unemphasizedSelectedContentBackgroundColor)
        #endif
    }
    
    // Filtrar eventos para el día seleccionado
    var filteredEventsForSelectedDay: [CalendarEvent] {
        let calendar = Calendar.current
        return googleEvents.filter { event in
            calendar.isDate(event.startDate, inSameDayAs: selectedDate)
        }
    }
    
    // Filtrar tareas para el día seleccionado
    var filteredTasksForSelectedDay: [GoogleCalendarTask] {
        let calendar = Calendar.current
        return googleTasks.filter { task in
            guard let dueDate = task.dueDate else {
                // Si no tiene fecha de vencimiento, mostrarla siempre
                return true
            }
            return calendar.isDate(dueDate, inSameDayAs: selectedDate)
        }
    }
    
    var body: some View {
        let scrollContent = ScrollView {
            VStack(spacing: 20) {
                calendarHeaderSection
                dateNavigationSection
                todayButtonSection
                viewModePickerSection
                calendarGridSection
                eventsAndTasksSection
                Spacer(minLength: 20)
            }
            #if os(macOS)
            .padding(.horizontal, 20)
            #endif
            .padding(.bottom, 80) // Add padding for CustomTabBar overlay
        }
        .background(systemGroupedBackground)
        #if os(macOS)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        #endif
        .task {
            await loadCalendarEvents()
        }
        .onChange(of: selectedDate) { oldValue, newValue in
            Task {
                await loadCalendarEvents()
            }
        }
        .onChange(of: viewMode) { oldValue, newValue in
            Task {
                await loadCalendarEvents()
            }
        }
        .alert("Sesión Expirada", isPresented: $showReauthAlert) {
            Button("Iniciar Sesión") {
                Task {
                    await authManager.signOut()
                }
            }
            Button("Cancelar", role: .cancel) {
                googleCalendarError = nil
            }
        } message: {
            Text("Tu sesión de Google Calendar ha expirado. ¿Deseas iniciar sesión nuevamente?")
        }
        .sheet(isPresented: $showCreateEventSheet) {
            CreateEventSheet(isPresented: $showCreateEventSheet) {
                await loadCalendarEvents()
            }
        }
        .sheet(isPresented: $showCreateTaskSheet) {
            CreateTaskSheetView(isPresented: $showCreateTaskSheet) {
                await loadCalendarEvents()
            }
        }
        
        #if os(iOS)
        return NavigationView {
            scrollContent
                .navigationTitle("Calendario")
        }
        #else
        return scrollContent
        #endif
    }
    
    // MARK: - View Components
    
    private var calendarHeaderSection: some View {
        HStack {
            Image(systemName: "calendar.badge.clock")
                .font(.system(size: 24))
                .foregroundColor(.blue)
            
            Text("Google Calendar")
                .font(.title2)
                .fontWeight(.bold)
            
            Spacer()
            
            Button(action: {
                showCreateEventSheet = true
            }) {
                Image(systemName: "plus.circle.fill")
                    .font(.system(size: 28))
                    .foregroundColor(.blue)
            }
        }
        .padding(.horizontal)
    }
    
    private var dateNavigationSection: some View {
        HStack(spacing: 20) {
            Button(action: {
                withAnimation {
                    selectedDate = Calendar.current.date(byAdding: .day, value: -1, to: selectedDate) ?? selectedDate
                }
            }) {
                Image(systemName: "chevron.left.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(.blue)
            }
            
            Spacer()
            
            VStack(spacing: 4) {
                Text(selectedDate, style: .date)
                    .font(.headline)
                Text(selectedDate.formatted(.dateTime.weekday(.wide)))
                    .font(.subheadline)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Button(action: {
                withAnimation {
                    selectedDate = Calendar.current.date(byAdding: .day, value: 1, to: selectedDate) ?? selectedDate
                }
            }) {
                Image(systemName: "chevron.right.circle.fill")
                    .font(.system(size: 32))
                    .foregroundColor(.blue)
            }
        }
        .padding(.horizontal)
    }
    
    private var todayButtonSection: some View {
        HStack {
            Spacer()
            Button(action: {
                withAnimation {
                    selectedDate = Date()
                }
            }) {
                HStack {
                    Image(systemName: "calendar.circle")
                    Text("Hoy")
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
                .background(Color.blue.opacity(0.1))
                .foregroundColor(.blue)
                .cornerRadius(20)
            }
            Spacer()
        }
        .padding(.top, 8)
    }
    
    private var viewModePickerSection: some View {
        Picker("Vista", selection: $viewMode) {
            ForEach(ViewMode.allCases, id: \.self) { mode in
                Text(mode.rawValue).tag(mode)
            }
        }
        .pickerStyle(.segmented)
        .padding(.horizontal)
    }
    
    private var calendarGridSection: some View {
        GoogleCalendarGrid(
            selectedDate: $selectedDate,
            events: googleEvents,
            viewMode: viewMode
        )
        .padding(.horizontal)
    }
    
    private var eventsAndTasksSection: some View {
        VStack(spacing: 0) {
            if let error = googleCalendarError {
                errorSection(error: error)
            } else {
                eventsSection
                Divider().padding(.horizontal)
                tasksSection
            }
        }
    }
    
    private func errorSection(error: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 40))
                .foregroundColor(.red)
            
            Text(error)
                .font(.subheadline)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Button("Reiniciar Sesión") {
                showReauthAlert = true
            }
            .foregroundColor(.blue)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 30)
    }
    
    private var eventsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Eventos", systemImage: "calendar")
                    .font(.headline)
                
                Spacer()
                
                Text("\(filteredEventsForSelectedDay.count)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.blue.opacity(0.2))
                    .foregroundColor(.blue)
                    .cornerRadius(10)
                
                Button(action: {
                    showCreateEventSheet = true
                }) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.blue)
                }
            }
            .padding(.horizontal)
            
            if filteredEventsForSelectedDay.isEmpty {
                emptyEventsView
            } else {
                ForEach(filteredEventsForSelectedDay) { event in
                    CalendarEventCard(event: event, onDelete: {
                        Task {
                            await deleteGoogleEvent(eventId: event.id)
                        }
                    })
                }
                .padding(.horizontal)
            }
        }
        .padding(.vertical, 10)
    }
    
    private var emptyEventsView: some View {
        HStack {
            Image(systemName: "calendar.badge.clock")
                .foregroundColor(.secondary)
            Text("No hay eventos para este día")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
    }
    
    private var tasksSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label("Tareas Pendientes", systemImage: "checklist")
                    .font(.headline)
                
                Spacer()
                
                Text("\(filteredTasksForSelectedDay.filter { !$0.isCompleted }.count)")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.green.opacity(0.2))
                    .foregroundColor(.green)
                    .cornerRadius(10)
                
                Button(action: {
                    showCreateTaskSheet = true
                }) {
                    Image(systemName: "plus.circle.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.green)
                }
            }
            .padding(.horizontal)
            
            if filteredTasksForSelectedDay.isEmpty {
                emptyTasksView
            } else {
                ForEach(filteredTasksForSelectedDay) { task in
                    TaskCard(
                        task: task,
                        onToggleComplete: { isCompleted in
                            print("Tarea \(task.title) completada: \(isCompleted)")
                        },
                        onDelete: {
                            print("Eliminar tarea: \(task.title)")
                        }
                    )
                }
                .padding(.horizontal)
            }
        }
        .padding(.vertical, 10)
    }
    
    private var emptyTasksView: some View {
        HStack {
            Image(systemName: "checkmark.circle")
                .foregroundColor(.secondary)
            Text("No hay tareas para este día")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 20)
    }
    
    private func loadCalendarEvents() async {
        // Obtener el inicio y fin según el modo de vista
        var calendar = Calendar.current
        calendar.timeZone = TimeZone.current
        
        let startOfPeriod = calendar.startOfDay(for: selectedDate)
        
        // Calcular el fin del período según el modo de vista
        let daysToAdd: Int
        switch viewMode {
        case .day:
            daysToAdd = 2  // 2 días para compensar zona horaria
        case .week:
            daysToAdd = 8  // 1 semana + 1 día extra
        case .month:
            daysToAdd = 32 // ~1 mes + días extra
        }
        
        guard let endOfPeriod = calendar.date(byAdding: .day, value: daysToAdd, to: startOfPeriod) else {
            return
        }
        
        print("📅 Buscando eventos (\(viewMode.rawValue)) desde: \(startOfPeriod) hasta: \(endOfPeriod)")
        
        // Cargar eventos del iPhone
        let localEvents = await calendarManager.fetchEvents(from: startOfPeriod, to: endOfPeriod)
        
        // Cargar eventos de Google Calendar si está autenticado
        var googleCalendarEvents: [CalendarEvent] = []
        
        if let accessToken = GoogleCalendarService.shared.hasValidToken() {
            // Obtener eventos de Google Calendar
            do {
                googleCalendarEvents = try await GoogleCalendarService.shared.fetchEvents(
                    accessToken: accessToken,
                    from: startOfPeriod,
                    to: endOfPeriod
                )
                
                await MainActor.run {
                    googleCalendarError = nil
                }
            } catch let error as GoogleCalendarError {
                if error == .unauthorized {
                    await MainActor.run {
                        googleCalendarError = "Sesión expirada. Por favor, inicia sesión nuevamente"
                        showReauthAlert = true
                    }
                } else {
                    await MainActor.run {
                        googleCalendarError = error.localizedDescription
                    }
                }
                print("Error loading Google Calendar events: \(error)")
            } catch {
                await MainActor.run {
                    googleCalendarError = "Error al cargar eventos de Google Calendar"
                }
                print("Error loading Google Calendar events: \(error)")
            }
        }
        
        // Cargar tareas de ejemplo (reemplazar con API real cuando esté lista)
        let sampleTasks = generateSampleTasks(for: startOfPeriod, to: endOfPeriod)
        
        // Actualizar eventos y tareas
        await MainActor.run {
            iphoneEvents = localEvents.sorted { $0.startDate < $1.startDate }
            googleEvents = googleCalendarEvents.sorted { $0.startDate < $1.startDate }
            googleTasks = sampleTasks
        }
    }
    
    // MARK: - Generate Sample Tasks (Temporal - reemplazar con API real)
    private func generateSampleTasks(for startDate: Date, to endDate: Date) -> [GoogleCalendarTask] {
        var tasks: [GoogleCalendarTask] = []
        
        // Tareas de ejemplo
        let calendar = Calendar.current
        
        // Tarea para hoy
        if calendar.isDate(Date(), inSameDayAs: selectedDate) {
            tasks.append(GoogleCalendarTask(
                id: "task-1",
                title: "Revisar correos importantes",
                notes: "Responder a los correos pendientes del equipo",
                isCompleted: false,
                dueDate: Date(),
                priority: .high
            ))
            
            tasks.append(GoogleCalendarTask(
                id: "task-2",
                title: "Preparar presentación",
                notes: "Slides para la reunión de mañana",
                isCompleted: false,
                dueDate: Date(),
                priority: .medium
            ))
            
            tasks.append(GoogleCalendarTask(
                id: "task-3",
                title: "Llamar al cliente",
                notes: nil,
                isCompleted: true,
                dueDate: Date(),
                priority: .low
            ))
        }
        
        // Tarea para días futuros
        if let tomorrow = calendar.date(byAdding: .day, value: 1, to: Date()),
           calendar.isDate(tomorrow, inSameDayAs: selectedDate) {
            tasks.append(GoogleCalendarTask(
                id: "task-4",
                title: "Reunión de equipo",
                notes: "Preparar agenda y puntos a discutir",
                isCompleted: false,
                dueDate: tomorrow,
                priority: .high
            ))
        }
        
        return tasks
    }
    
    private func deleteGoogleEvent(eventId: String) async {
        guard let accessToken = GoogleCalendarService.shared.hasValidToken() else {
            return
        }
        
        do {
            try await GoogleCalendarService.shared.deleteEvent(accessToken: accessToken, eventId: eventId)
            await loadCalendarEvents()
        } catch {
            print("Error deleting event: \(error)")
        }
    }
    
}

// MARK: - Google Calendar Grid (Calendario Visual Interactivo)
struct GoogleCalendarGrid: View {
    @Binding var selectedDate: Date
    let events: [CalendarEvent]
    let viewMode: HistoryView.ViewMode
    
    var calendar = Calendar.current
    
    private var secondarySystemGroupedBackground: Color {
        #if os(iOS)
        return Color(UIColor.secondarySystemGroupedBackground)
        #elseif os(macOS)
        return Color(NSColor.windowBackgroundColor)
        #endif
    }
    
    // Obtener días del mes actual
    private var daysInMonth: [Date] {
        guard let monthInterval = calendar.dateInterval(of: .month, for: selectedDate) else {
            return []
        }
        
        var days: [Date] = []
        var currentDate = monthInterval.start
        
        while currentDate < monthInterval.end {
            days.append(currentDate)
            currentDate = calendar.date(byAdding: .day, value: 1, to: currentDate)!
        }
        
        return days
    }
    
    // Verificar si una fecha tiene eventos
    private func hasEvents(on date: Date) -> Bool {
        events.contains { calendar.isDate($0.startDate, inSameDayAs: date) }
    }
    
    // Contar eventos en una fecha
    private func eventCount(on date: Date) -> Int {
        events.filter { calendar.isDate($0.startDate, inSameDayAs: date) }.count
    }
    
    var body: some View {
        VStack(spacing: 10) {
            // Encabezado con mes y año
            Text(selectedDate.formatted(.dateTime.month(.wide).year()))
                .font(.title3)
                .fontWeight(.semibold)
                .foregroundColor(.blue)
            
            // Días de la semana
            HStack(spacing: 0) {
                ForEach(calendar.shortWeekdaySymbols, id: \.self) { day in
                    Text(day)
                        .font(.caption)
                        .fontWeight(.medium)
                        .frame(maxWidth: .infinity)
                        .foregroundColor(.secondary)
                }
            }
            
            // Grid de días del mes
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 4), count: 7), spacing: 8) {
                // Días vacíos al inicio
                ForEach(0..<firstWeekdayOffset(), id: \.self) { _ in
                    Color.clear
                        .frame(height: 50)
                }
                
                // Días del mes
                ForEach(daysInMonth, id: \.self) { date in
                    DayCell(
                        date: date,
                        isSelected: calendar.isDate(date, inSameDayAs: selectedDate),
                        isToday: calendar.isDateInToday(date),
                        hasEvents: hasEvents(on: date),
                        eventCount: eventCount(on: date)
                    )
                    .onTapGesture {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                            selectedDate = date
                        }
                    }
                }
            }
        }
        .padding()
        .background(secondarySystemGroupedBackground)
        .cornerRadius(16)
    }
    
    private func firstWeekdayOffset() -> Int {
        guard let firstDay = daysInMonth.first else { return 0 }
        let weekday = calendar.component(.weekday, from: firstDay)
        return weekday - calendar.firstWeekday
    }
}

// MARK: - Day Cell (Celda de día en el calendario)
struct DayCell: View {
    let date: Date
    let isSelected: Bool
    let isToday: Bool
    let hasEvents: Bool
    let eventCount: Int
    
    var body: some View {
        VStack(spacing: 4) {
            Text("\(Calendar.current.component(.day, from: date))")
                .font(.system(size: 16, weight: isSelected ? .bold : .regular))
                .foregroundColor(textColor)
            
            // Indicador de eventos
            if hasEvents {
                HStack(spacing: 2) {
                    Circle()
                        .fill(Color.blue)
                        .frame(width: 4, height: 4)
                    
                    if eventCount > 1 {
                        Text("\(eventCount)")
                            .font(.system(size: 8))
                            .foregroundColor(.blue)
                    }
                }
            }
        }
        .frame(height: 50)
        .frame(maxWidth: .infinity)
        .background(backgroundColor)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(borderColor, lineWidth: isToday ? 2 : 0)
        )
    }
    
    private var backgroundColor: Color {
        if isSelected {
            return .blue
        } else if hasEvents {
            return Color.blue.opacity(0.1)
        } else {
            #if os(iOS)
            return Color(UIColor.tertiarySystemGroupedBackground)
            #elseif os(macOS)
            return Color(NSColor.unemphasizedSelectedContentBackgroundColor)
            #endif
        }
    }
    
    private var textColor: Color {
        if isSelected {
            return .white
        } else if isToday {
            return .blue
        } else {
            return .primary
        }
    }
    
    private var borderColor: Color {
        isToday ? .blue : .clear
    }
}

struct CalendarEventCard: View {
    let event: CalendarEvent
    var onDelete: (() async -> Void)?
    
    private var secondarySystemGroupedBackground: Color {
        #if os(iOS)
        return Color(UIColor.secondarySystemGroupedBackground)
        #elseif os(macOS)
        return Color(NSColor.windowBackgroundColor)
        #endif
    }
    
    var body: some View {
        HStack(spacing: 12) {
            VStack {
                Image(systemName: "clock")
                    .foregroundColor(event.calendar == "Google Calendar" ? .blue : LyrionTheme.primaryPurple)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(event.title)
                    .font(.headline)
                
                if event.isAllDay {
                    Text("Todo el día")
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    HStack(spacing: 4) {
                        Text(formatTime(event.startDate))
                        Text("-")
                        Text(formatTime(event.endDate))
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                
                HStack(spacing: 8) {
                    if let notes = event.notes {
                        Text(notes)
                            .font(.caption2)
                            .foregroundColor(.secondary)
                            .lineLimit(2)
                    }
                    
                    Text(event.calendar)
                        .font(.caption2)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(event.calendar == "Google Calendar" ? Color.blue.opacity(0.2) : LyrionTheme.primaryPurple.opacity(0.2))
                        .cornerRadius(6)
                }
            }
            
            Spacer()
        }
        .padding()
        .background(secondarySystemGroupedBackground)
        .cornerRadius(12)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if onDelete != nil {
                Button(role: .destructive) {
                    Task {
                        await onDelete?()
                    }
                } label: {
                    Label("Eliminar", systemImage: "trash")
                }
            }
        }
    }
    
    private func formatTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Google Calendar Task Model
struct GoogleCalendarTask: Identifiable {
    let id: String
    let title: String
    let notes: String?
    var isCompleted: Bool
    let dueDate: Date?
    let priority: TaskPriority
    
    enum TaskPriority: String {
        case high = "Alta"
        case medium = "Media"
        case low = "Baja"
        case none = "Sin prioridad"
    }
}

// MARK: - Task Card (Tarjeta de Tarea)
struct TaskCard: View {
    @State var task: GoogleCalendarTask
    var onToggleComplete: ((Bool) -> Void)?
    var onDelete: (() async -> Void)?
    
    private var secondarySystemGroupedBackground: Color {
        #if os(iOS)
        return Color(UIColor.secondarySystemGroupedBackground)
        #elseif os(macOS)
        return Color(NSColor.windowBackgroundColor)
        #endif
    }
    
    var body: some View {
        HStack(spacing: 12) {
            // Checkbox
            Button(action: {
                task.isCompleted.toggle()
                onToggleComplete?(task.isCompleted)
            }) {
                Image(systemName: task.isCompleted ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 28))
                    .foregroundColor(task.isCompleted ? .green : .gray)
            }
            
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text(task.title)
                        .font(.headline)
                        .strikethrough(task.isCompleted, color: .gray)
                        .foregroundColor(task.isCompleted ? .secondary : .primary)
                    
                    Spacer()
                    
                    // Badge de prioridad
                    if task.priority != .none && !task.isCompleted {
                        Text(task.priority.rawValue)
                            .font(.caption2)
                            .fontWeight(.semibold)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(priorityColor.opacity(0.2))
                            .foregroundColor(priorityColor)
                            .cornerRadius(6)
                    }
                }
                
                if let notes = task.notes, !notes.isEmpty {
                    Text(notes)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(2)
                }
                
                HStack(spacing: 8) {
                    if let dueDate = task.dueDate {
                        Label(formatDate(dueDate), systemImage: "calendar")
                            .font(.caption2)
                            .foregroundColor(isOverdue ? .red : .secondary)
                    }
                    
                    Label("Google Calendar", systemImage: "checkmark.circle.fill")
                        .font(.caption2)
                        .foregroundColor(.blue)
                }
            }
            
            Spacer()
        }
        .padding()
        .background(secondarySystemGroupedBackground)
        .cornerRadius(12)
        .opacity(task.isCompleted ? 0.7 : 1.0)
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            if onDelete != nil {
                Button(role: .destructive) {
                    Task {
                        await onDelete?()
                    }
                } label: {
                    Label("Eliminar", systemImage: "trash")
                }
            }
        }
    }
    
    private var priorityColor: Color {
        switch task.priority {
        case .high: return .red
        case .medium: return .orange
        case .low: return .blue
        case .none: return .gray
        }
    }
    
    private var isOverdue: Bool {
        guard let dueDate = task.dueDate else { return false }
        return dueDate < Date() && !task.isCompleted
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .none
        return formatter.string(from: date)
    }
}

// MARK: - Create Task Sheet
struct CreateTaskSheetView: View {
    @Binding var isPresented: Bool
    var onTaskCreated: () async -> Void
    
    @State private var title: String = ""
    @State private var notes: String = ""
    @State private var hasDueDate: Bool = true
    @State private var dueDate: Date = Date()
    @State private var priority: GoogleCalendarTask.TaskPriority = .medium
    @State private var isCreating: Bool = false
    
    var body: some View {
        NavigationView {
            Form {
                Section(header: Text("Información de la Tarea")) {
                    TextField("Título de la tarea", text: $title)
                    
                    Picker("Prioridad", selection: $priority) {
                        Text("Alta 🔴").tag(GoogleCalendarTask.TaskPriority.high)
                        Text("Media 🟠").tag(GoogleCalendarTask.TaskPriority.medium)
                        Text("Baja 🔵").tag(GoogleCalendarTask.TaskPriority.low)
                        Text("Sin prioridad").tag(GoogleCalendarTask.TaskPriority.none)
                    }
                }
                
                Section(header: Text("Fecha de Vencimiento")) {
                    Toggle("Establecer fecha", isOn: $hasDueDate)
                    
                    if hasDueDate {
                        DatePicker("Fecha", selection: $dueDate, displayedComponents: [.date])
                    }
                }
                
                Section(header: Text("Notas (Opcional)")) {
                    TextEditor(text: $notes)
                        .frame(height: 100)
                }
            }
            .navigationTitle("Nueva Tarea")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                #if os(iOS)
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancelar") {
                        isPresented = false
                    }
                    .disabled(isCreating)
                }
                
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Crear") {
                        Task {
                            await createTask()
                        }
                    }
                    .disabled(title.isEmpty || isCreating)
                }
                #elseif os(macOS)
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        isPresented = false
                    }
                    .disabled(isCreating)
                }
                
                ToolbarItem(placement: .confirmationAction) {
                    Button("Crear") {
                        Task {
                            await createTask()
                        }
                    }
                    .disabled(title.isEmpty || isCreating)
                }
                #endif
            }
        }
    }
    
    private func createTask() async {
        isCreating = true
        
        // Aquí irá la lógica para crear la tarea en Google Calendar
        // Por ahora solo cerramos el sheet
        
        try? await Task.sleep(nanoseconds: 500_000_000) // Simular delay
        
        await MainActor.run {
            isPresented = false
        }
        
        await onTaskCreated()
    }
}


