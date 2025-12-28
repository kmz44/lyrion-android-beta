//
//  ChatsSettingsView.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/6/24.
//

import SwiftUI

struct ChatsSettingsView: View {
    @EnvironmentObject var appManager: AppManager
    @Environment(\.modelContext) var modelContext
    @State var systemPrompt = ""
    @State var deleteAllChats = false
    @Binding var currentThread: Thread?
    
    var body: some View {
        Form {
            Section(header: Text(NSLocalizedString("chats.system_prompt", comment: "")), 
                    footer: Text("Instrucciones que guían el comportamiento de la IA. Cambia esto para personalizar cómo responde el modelo.")) {
                TextEditor(text: $appManager.systemPrompt)
                    .textEditorStyle(.plain)
                    .frame(minHeight: 150)
                
                Button(action: {
                    restoreDefaultPrompt()
                }) {
                    HStack {
                        Image(systemName: "arrow.counterclockwise")
                        Text("Restaurar prompt por defecto")
                    }
                    .font(.system(size: 14))
                }
                .buttonStyle(.borderless)
            }
            
            if appManager.userInterfaceIdiom == .phone {
                Section {
                    Toggle(NSLocalizedString("chats.haptics", comment: ""), isOn: $appManager.shouldPlayHaptics)
                        .tint(.green)
                }
            }
            
            Section {
                Button {
                    deleteAllChats.toggle()
                } label: {
                    Label(NSLocalizedString("chats.delete_all", comment: ""), systemImage: "trash")
                        .foregroundStyle(.red)
                }
                .alert(NSLocalizedString("chats.confirm_delete", comment: ""), isPresented: $deleteAllChats) {
                    Button(NSLocalizedString("common.cancel", comment: ""), role: .cancel) {
                        deleteAllChats = false
                    }
                    Button(NSLocalizedString("common.delete", comment: ""), role: .destructive) {
                        deleteChats()
                    }
                }
                .buttonStyle(.borderless)
            }
        }
        .formStyle(.grouped)
        .navigationTitle(NSLocalizedString("chats.title", comment: ""))
        #if os(iOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
    }
    
    func deleteChats() {
        do {
            currentThread = nil
            try modelContext.delete(model: Thread.self)
            try modelContext.delete(model: Message.self)
        } catch {
            print("Failed to delete.")
        }
    }
    
    func restoreDefaultPrompt() {
        appManager.systemPrompt = """
You are a helpful assistant. When explaining mathematical, scientific, chemical, or physical concepts, ALWAYS use LaTeX formatting:
- Use inline LaTeX with single $ for variables and short expressions: $ x $, $ F = ma $, $ E = mc^2 $
- Use display LaTeX with double $$ for complex formulas and equations
- For chemistry, use LaTeX: $ \\text{H}_2\\text{O} $, $ \\text{CO}_2 $
- Always format mathematical expressions, equations, chemical formulas, and physics equations in LaTeX
- Use proper LaTeX syntax: subscripts with _, superscripts with ^, fractions with \\frac{}{}, Greek letters, etc.

Examples:
- Force: $ F = G \\cdot \\frac{m_1 \\cdot m_2}{r^2} $
- Chemistry: $ 2\\text{H}_2 + \\text{O}_2 \\rightarrow 2\\text{H}_2\\text{O} $
- Energy: $$ E = mc^2 $$
"""
    }
}

#Preview {
    ChatsSettingsView(currentThread: .constant(nil))
}
