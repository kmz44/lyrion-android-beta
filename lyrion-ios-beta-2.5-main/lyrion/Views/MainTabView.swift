//
//  MainTabView.swift
//  Lyrion
//

import SwiftUI
import SwiftData

struct MainTabView: View {
    @EnvironmentObject var appManager: AppManager
    @Environment(LLMEvaluator.self) var llm
    @State private var selectedTab = 2
    @Binding var currentThread: Thread?
    @FocusState.Binding var isPromptFocused: Bool
    @Binding var showSettings: Bool
    @Binding var showChats: Bool
    
    // Track keyboard visibility
    @State private var isKeyboardVisible = false
    
    var body: some View {
        #if os(macOS)
        // Para macOS: usar navegación con barra lateral izquierda (estilo Discord/YouTube)
        HStack(spacing: 0) {
            // Barra lateral izquierda con iconos
            MacSidebarTabBar(selectedTab: $selectedTab)
                .frame(width: 80)
            
            Divider()
            
            // Contenido según tab seleccionado
            Group {
                switch selectedTab {
                case 0:
                    HomeView()
                case 1:
                    SearchView()
                case 2:
                    ChatView(currentThread: $currentThread, isPromptFocused: $isPromptFocused, showChats: $showChats, showSettings: $showSettings)
                case 3:
                    HistoryView(currentThread: $currentThread, isPromptFocused: $isPromptFocused, selectedTab: $selectedTab)
                case 4:
                    ProfileView(showSettings: $showSettings)
                default:
                    HomeView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        #else
        // Para iOS: Navegación personalizada sin TabView para evitar conflictos de gestos
        ZStack(alignment: .bottom) {
            // Contenido Principal
            Group {
                switch selectedTab {
                case 0:
                    NavigationStack {
                        SearchView()
                    }
                case 1:
                    NavigationStack {
                        ChatView(currentThread: $currentThread, isPromptFocused: $isPromptFocused, showChats: $showChats, showSettings: $showSettings)
                    }
                case 2:
                    NavigationStack {
                        HomeView()
                    }
                case 3:
                    NavigationStack {
                        HistoryView(currentThread: $currentThread, isPromptFocused: $isPromptFocused, selectedTab: $selectedTab)
                    }
                case 4:
                    NavigationStack {
                        ProfileView(showSettings: $showSettings)
                    }
                default:
                    NavigationStack {
                        HomeView()
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .simultaneousGesture(
                DragGesture(minimumDistance: 20, coordinateSpace: .local)
                    .onEnded { value in
                        // Detectar dirección del deslizamiento
                        let horizontalAmount = value.translation.width
                        let verticalAmount = value.translation.height
                        
                        // Solo procesar si es principalmente horizontal
                        if abs(horizontalAmount) > abs(verticalAmount) && abs(horizontalAmount) > 50 {
                            if horizontalAmount < 0 {
                                // Deslizar a la izquierda (Siguiente Tab)
                                if selectedTab < 4 {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        selectedTab += 1
                                    }
                                }
                            } else {
                                // Deslizar a la derecha (Tab Anterior)
                                if selectedTab > 0 {
                                    withAnimation(.easeInOut(duration: 0.2)) {
                                        selectedTab -= 1
                                    }
                                }
                            }
                        }
                    }
            )
            
            // Barra de navegación inferior personalizada
            if !isKeyboardVisible && !appManager.isFullScreenMode {
                CustomTabBar(selectedTab: $selectedTab)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .animation(.easeInOut(duration: 0.25), value: isKeyboardVisible)
            }
        }
        .ignoresSafeArea(.keyboard, edges: .bottom)
        .onAppear {
            setupKeyboardObservers()
        }
        .onDisappear {
            removeKeyboardObservers()
        }
        #endif
    }
    
    private func hideKeyboard() {
        isPromptFocused = false
        #if os(iOS)
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        #endif
    }
    
    #if os(iOS)
    private func setupKeyboardObservers() {
        NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillShowNotification, object: nil, queue: .main) { _ in
            withAnimation(.easeInOut(duration: 0.25)) {
                isKeyboardVisible = true
            }
        }
        
        NotificationCenter.default.addObserver(forName: UIResponder.keyboardWillHideNotification, object: nil, queue: .main) { _ in
            withAnimation(.easeInOut(duration: 0.25)) {
                isKeyboardVisible = false
            }
        }
    }
    
    private func removeKeyboardObservers() {
        NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillShowNotification, object: nil)
        NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillHideNotification, object: nil)
    }
    #else
    private func setupKeyboardObservers() {}
    private func removeKeyboardObservers() {}
    #endif
}
