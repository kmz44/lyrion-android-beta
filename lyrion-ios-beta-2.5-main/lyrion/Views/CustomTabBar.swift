//
//  CustomTabBar.swift
//  Lyrion
//

import SwiftUI
#if os(macOS)
import AppKit
#endif

#if os(macOS)
// Barra lateral para macOS (estilo Discord/YouTube)
struct MacSidebarTabBar: View {
    @Binding var selectedTab: Int
    
    var body: some View {
        VStack(spacing: 8) {
            Spacer()
                .frame(height: 20)
            
            // Tab 1: Inicio
            MacSidebarButton(
                icon: "house",
                isSelected: selectedTab == 0
            ) {
                selectedTab = 0
            }
            
            // Tab 2: Búsqueda
            MacSidebarButton(
                icon: "magnifyingglass",
                isSelected: selectedTab == 1
            ) {
                selectedTab = 1
            }
            
            // Tab 3: Chat
            MacSidebarButton(
                icon: "message",
                isSelected: selectedTab == 2
            ) {
                selectedTab = 2
            }
            
            // Tab 4: Historial
            MacSidebarButton(
                icon: "calendar",
                isSelected: selectedTab == 3
            ) {
                selectedTab = 3
            }
            
            // Tab 5: Perfil
            MacSidebarButton(
                icon: "person",
                isSelected: selectedTab == 4
            ) {
                selectedTab = 4
            }
            
            Spacer()
        }
        .frame(maxWidth: .infinity)
        .background(Color(NSColor.controlBackgroundColor))
    }
}

// Botón de la barra lateral (solo ícono, sin texto)
struct MacSidebarButton: View {
    let icon: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                Image(systemName: {
                    if isSelected {
                        switch icon {
                        case "calendar":
                            return "calendar.circle.fill"
                        case "magnifyingglass":
                            return "magnifyingglass.circle.fill"
                        default:
                            return "\(icon).fill"
                        }
                    } else {
                        return icon
                    }
                }())
                .font(.system(size: 28, weight: .regular))
                .foregroundColor(isSelected ? LyrionTheme.primaryPurple : Color.gray)
                .frame(width: 48, height: 48)
                .background(
                    isSelected ? LyrionTheme.primaryPurple.opacity(0.15) : Color.clear
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
        .help(getTooltip())
    }
    
    private func getTooltip() -> String {
        switch icon {
        case "house": return "Inicio"
        case "magnifyingglass": return "Búsqueda"
        case "message": return "Chat"
        case "calendar": return "Historial"
        case "person": return "Perfil"
        default: return ""
        }
    }
}
#endif

struct CustomTabBar: View {
    @Binding var selectedTab: Int
    @EnvironmentObject var appManager: AppManager
    
    private var backgroundColor: some View {
        Group {
            if selectedTab == 2 {
                // Transparent glass for Home tab
                Color.clear
            } else {
                // Solid background for other tabs
                #if os(iOS)
                Color(uiColor: .systemBackground)
                #elseif os(macOS)
                Color(NSColor.controlBackgroundColor).opacity(0.95)
                #else
                Color(nsColor: .controlBackgroundColor)
                #endif
            }
        }
    }
    
    var body: some View {
        #if os(macOS)
        // Para macOS: barra horizontal superior
        HStack(spacing: 20) {
            Spacer()
            
            // Tab 1: Inicio
            MacTabBarButton(
                icon: "house",
                title: "Inicio",
                isSelected: selectedTab == 0
            ) {
                selectedTab = 0
            }
            
            // Tab 2: Búsqueda
            MacTabBarButton(
                icon: "magnifyingglass",
                title: "Búsqueda",
                isSelected: selectedTab == 1
            ) {
                selectedTab = 1
            }
            
            // Tab 3: Chat (Chatbot)
            MacTabBarButton(
                icon: "message",
                title: "Chat",
                isSelected: selectedTab == 2
            ) {
                selectedTab = 2
            }
            
            // Tab 4: Calendario (Historial)
            MacTabBarButton(
                icon: "calendar",
                title: "Historial",
                isSelected: selectedTab == 3
            ) {
                selectedTab = 3
            }
            
            // Tab 5: Perfil
            MacTabBarButton(
                icon: "person",
                title: "Perfil",
                isSelected: selectedTab == 4
            ) {
                selectedTab = 4
            }
            
            Spacer()
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .background(
            backgroundColor
                .shadow(color: Color.black.opacity(0.05), radius: 2, x: 0, y: 1)
        )
        #else
        // Para iOS: barra vertical inferior
        HStack(spacing: 0) {
            // Tab 1: Búsqueda (Índice 0)
            TabBarButton(
                icon: "magnifyingglass",
                title: "Búsqueda",
                isSelected: selectedTab == 0
            ) {
                appManager.playHaptic()
                selectedTab = 0
            }
            
            // Tab 2: Chat (Índice 1)
            TabBarButton(
                icon: "message",
                title: "Chat",
                isSelected: selectedTab == 1
            ) {
                appManager.playHaptic()
                selectedTab = 1
            }
            
            // Tab 3: Inicio (Índice 2 - CENTRO)
            TabBarButton(
                icon: "house",
                title: "Inicio",
                isSelected: selectedTab == 2
            ) {
                appManager.playHaptic()
                selectedTab = 2
            }
            
            // Tab 4: Historial (Índice 3)
            TabBarButton(
                icon: "calendar",
                title: "Historial",
                isSelected: selectedTab == 3
            ) {
                appManager.playHaptic()
                selectedTab = 3
            }
            
            // Tab 5: Perfil (Índice 4)
            TabBarButton(
                icon: "person",
                title: "Perfil",
                isSelected: selectedTab == 4
            ) {
                appManager.playHaptic()
                selectedTab = 4
            }
        }
        .padding(.vertical, 8)
        .padding(.bottom, 8)
        .background(
            ZStack {
                backgroundColor
                
                // Add glass material only on Home tab (Index 2)
                if selectedTab == 2 {
                    // Clear glass for tab bar
                    Color.black.opacity(0.01) // Almost invisible
                        .background(.ultraThinMaterial.opacity(0.4)) // Very light blur
                        .overlay(
                            Rectangle()
                                .frame(height: 0.5)
                                .foregroundColor(Color.white.opacity(0.2)),
                            alignment: .top
                        )
                }
            }
            .shadow(color: Color.black.opacity(selectedTab == 2 ? 0.1 : 0.1), 
                    radius: 10, x: 0, y: -5)
        )
        #endif
    }
}

// Botón para macOS (horizontal)
struct MacTabBarButton: View {
    let icon: String
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: {
                    if isSelected {
                        switch icon {
                        case "calendar":
                            return "calendar.circle.fill"
                        case "magnifyingglass":
                            return "magnifyingglass.circle.fill"
                        default:
                            return "\(icon).fill"
                        }
                    } else {
                        return icon
                    }
                }())
                    .font(.system(size: 20))
                    .foregroundColor(isSelected ? LyrionTheme.primaryPurple : Color.gray)
                
                Text(title)
                    .font(.system(size: 14, weight: isSelected ? .semibold : .regular))
                    .foregroundColor(isSelected ? LyrionTheme.primaryPurple : Color.gray)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                isSelected ? LyrionTheme.primaryPurple.opacity(0.1) : Color.clear
            )
            .cornerRadius(10)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}

struct TabBarButton: View {
    let icon: String
    let title: String
    let isSelected: Bool
    let action: () -> Void
    
    private var grayColor: Color {
        #if os(iOS)
        return Color(uiColor: .systemGray)
        #else
        return Color.gray
        #endif
    }
    
    var body: some View {
        Button(action: action) {
            VStack(spacing: 4) {
                // Usar íconos especiales para aquellos que no tienen versión .fill
                Image(systemName: {
                    if isSelected {
                        switch icon {
                        case "calendar":
                            return "calendar.circle.fill"
                        case "magnifyingglass":
                            return "magnifyingglass.circle.fill"
                        default:
                            return "\(icon).fill"
                        }
                    } else {
                        return icon
                    }
                }())
                    .font(.system(size: 24))
                    .foregroundColor(isSelected ? LyrionTheme.primaryPurple : grayColor)
                
                Text(title)
                    .font(.system(size: 12))
                    .fontWeight(isSelected ? .medium : .regular)
                    .foregroundColor(isSelected ? LyrionTheme.primaryPurple : grayColor)
            }
            .frame(maxWidth: .infinity)
            .contentShape(Rectangle())
        }
        .buttonStyle(PlainButtonStyle())
    }
}
