//
//  ChatView.swift
//  Lyrion
//
//  Created by Jordan Singer on 12/3/24.
//

import MarkdownUI
import SwiftUI
import PhotosUI

struct ChatView: View {
    @EnvironmentObject var appManager: AppManager
    @Environment(\.modelContext) var modelContext
    @Binding var currentThread: Thread?
    @Environment(LLMEvaluator.self) var llm
    @Namespace var bottomID
    @State var showModelPicker = false
    @State var prompt = ""
    @FocusState.Binding var isPromptFocused: Bool
    @Binding var showChats: Bool
    @Binding var showSettings: Bool
    
    @State var thinkingTime: TimeInterval?
    
    @State private var generatingThreadID: UUID?
    
    // Image picker state
    @State private var selectedPhoto: PhotosPickerItem?
    @State private var selectedImageData: Data?
    @State private var showImagePreview = false
    
    // Chat history panel for macOS
    #if os(macOS)
    @State private var showChatHistory = false
    #endif

    var isPromptEmpty: Bool {
        prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    let platformBackgroundColor: Color = {
        #if os(iOS)
        return Color(UIColor.tertiarySystemBackground)
        #elseif os(visionOS)
        return Color(UIColor.separator)
        #elseif os(macOS)
        return Color(NSColor.secondarySystemFill)
        #endif
    }()
    
    let systemBackgroundColor: Color = {
        #if os(iOS)
        return Color(UIColor.systemBackground)
        #elseif os(macOS)
        return Color(NSColor.controlBackgroundColor)
        #else
        return Color.clear
        #endif
    }()

    var chatContentView: some View {
        VStack(spacing: 0) {
            if let currentThread = currentThread {
                ConversationView(thread: currentThread, generatingThreadID: generatingThreadID)
                    .background(systemBackgroundColor)
            } else {
                Spacer()
                Image(.lyrionLogo)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 64, height: 64)
                    .opacity(0.3)
                Spacer()
            }
            
            // ADVERTENCIA DE MEMORIA: Mostrar cuando se detiene por presión de memoria
            if let memoryWarning = llm.memoryWarning {
                VStack(spacing: 8) {
                    Text(memoryWarning)
                        .font(.system(size: 13))
                        .foregroundColor(.white)
                        .multilineTextAlignment(.leading)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.red.opacity(0.9))
                        .cornerRadius(12)
                        .padding(.horizontal)
                    
                    Button {
                        llm.memoryWarning = nil
                    } label: {
                        Text("Entendido")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(.white)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 24)
                            .background(LyrionTheme.primaryPurple)
                            .cornerRadius(20)
                    }
                    .padding(.bottom, 8)
                }
            }

            HStack(alignment: .bottom) {
                modelPickerButton
                chatInput
            }
            .padding()
        }
        .padding(.bottom, 80) // Add padding for CustomTabBar overlay
    }

    var chatInput: some View {
        VStack(spacing: 0) {
            // Image preview
            #if os(iOS)
            if let imageData = selectedImageData, let uiImage = UIImage(data: imageData) {
                HStack {
                    Image(uiImage: uiImage)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 80, height: 80)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    
                    Spacer()
                    
                    Button {
                        selectedImageData = nil
                        selectedPhoto = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
                .padding(8)
                .background(platformBackgroundColor.opacity(0.5))
            }
            #elseif os(macOS)
            if let imageData = selectedImageData, let nsImage = NSImage(data: imageData) {
                HStack {
                    Image(nsImage: nsImage)
                        .resizable()
                        .scaledToFill()
                        .frame(width: 80, height: 80)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    
                    Spacer()
                    
                    Button {
                        selectedImageData = nil
                        selectedPhoto = nil
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
                .padding(8)
                .background(platformBackgroundColor.opacity(0.5))
            }
            #endif
            
            HStack(alignment: .bottom, spacing: 0) {
                // Image picker button - visible with camera icon
                PhotosPicker(selection: $selectedPhoto, matching: .images) {
                    Image(systemName: selectedImageData != nil ? "photo.fill.on.rectangle.fill" : "photo.on.rectangle")
                        .foregroundColor(llm.modelConfiguration.supportsVision ? 
                                       (selectedImageData != nil ? LyrionTheme.primaryPurple : .primary) : 
                                       .gray.opacity(0.5))
                    #if os(iOS) || os(visionOS)
                        .font(.system(size: 20))
                        .frame(width: 32, height: 32)
                        .padding(.leading, 8)
                        .padding(.trailing, 4)
                    #elseif os(macOS)
                        .font(.system(size: 24))
                        .frame(width: 32, height: 32)
                        .padding(.leading, 12)
                    #endif
                }
                .disabled(!llm.modelConfiguration.supportsVision)
                .onChange(of: selectedPhoto) { _, newValue in
                    Task {
                        if let data = try? await newValue?.loadTransferable(type: Data.self) {
                            selectedImageData = data
                        }
                    }
                }
                
                TextField(NSLocalizedString("message.placeholder", comment: ""), text: $prompt, axis: .vertical)
                    .focused($isPromptFocused)
                    .textFieldStyle(.plain)
                #if os(iOS) || os(visionOS)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                    .frame(minHeight: 48)
                #elseif os(macOS)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .frame(minHeight: 44)
                    .font(.system(size: 16))
                    .onSubmit {
                        handleShiftReturn()
                    }
                    .submitLabel(.send)
                #endif
                #if os(iOS)
                .onSubmit {
                    isPromptFocused = true
                    generate()
                }
                #endif

                if llm.running {
                    stopButton
                } else {
                    generateButton
                }
            }
        }
        #if os(iOS) || os(visionOS)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(platformBackgroundColor)
        )
        #elseif os(macOS)
        .background(
            RoundedRectangle(cornerRadius: 20)
                .fill(platformBackgroundColor)
        )
        .padding(.horizontal, 20)
        .padding(.bottom, 16)
        #endif
    }

    var modelPickerButton: some View {
        Button {
            appManager.playHaptic()
            showModelPicker.toggle()
        } label: {
            Group {
                Image(systemName: "chevron.up")
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                #if os(iOS) || os(visionOS)
                    .frame(width: 16)
                #elseif os(macOS)
                    .frame(width: 12)
                #endif
                    .tint(.primary)
            }
            #if os(iOS) || os(visionOS)
            .frame(width: 48, height: 48)
            #elseif os(macOS)
            .frame(width: 32, height: 32)
            #endif
            .background(
                Circle()
                    .fill(platformBackgroundColor)
            )
        }
        #if os(macOS) || os(visionOS)
        .buttonStyle(.plain)
        #endif
    }

    var generateButton: some View {
        Button {
            generate()
        } label: {
            Image(systemName: "arrow.up.circle.fill")
                .resizable()
                .aspectRatio(contentMode: .fit)
            #if os(iOS) || os(visionOS)
                .frame(width: 24, height: 24)
            #else
                .frame(width: 28, height: 28)
            #endif
        }
        .disabled(isPromptEmpty)
        #if os(iOS) || os(visionOS)
            .padding(.trailing, 12)
            .padding(.bottom, 12)
        #else
            .padding(.trailing, 12)
            .padding(.bottom, 10)
        #endif
        #if os(macOS) || os(visionOS)
        .buttonStyle(.plain)
        #endif
    }

    var stopButton: some View {
        Button {
            llm.stop()
        } label: {
            Image(systemName: "stop.circle.fill")
                .resizable()
                .aspectRatio(contentMode: .fit)
            #if os(iOS) || os(visionOS)
                .frame(width: 24, height: 24)
            #else
                .frame(width: 28, height: 28)
            #endif
        }
        .disabled(llm.cancelled)
        #if os(iOS) || os(visionOS)
            .padding(.trailing, 12)
            .padding(.bottom, 12)
        #else
            .padding(.trailing, 12)
            .padding(.bottom, 10)
        #endif
        #if os(macOS) || os(visionOS)
        .buttonStyle(.plain)
        #endif
    }

    var chatTitle: String {
        if let currentThread = currentThread {
            if let firstMessage = currentThread.sortedMessages.first {
                return firstMessage.content
            }
        }

        return "chat"
    }

    var body: some View {
        Group {
            #if os(macOS)
            HStack(spacing: 0) {
                // Chat history sidebar panel
                if showChatHistory {
                    ChatsListView(currentThread: $currentThread, isPromptFocused: $isPromptFocused)
                        .frame(width: 250)
                        .transition(.move(edge: .leading))
                    
                    Divider()
                }
                
                // Main chat content
                chatContentView
            }
            .animation(.easeInOut(duration: 0.3), value: showChatHistory)
            #else
            chatContentView
            #endif
        }
        .navigationTitle(chatTitle)
        #if os(iOS) || os(visionOS)
        .navigationBarTitleDisplayMode(.inline)
        #endif
        #if os(macOS)
        .toolbar {
            ToolbarItem(placement: .navigation) {
                Button(action: {
                    showChatHistory.toggle()
                }) {
                    Label("Historial", systemImage: "bubble.left.and.bubble.right")
                }
                .help("Mostrar historial de chats")
            }
        }
        #endif
        .sheet(isPresented: $showModelPicker) {
            NavigationStack {
                ModelsSettingsView()
                    .environment(llm)
                #if os(visionOS)
                    .toolbar {
                        ToolbarItem(placement: .topBarLeading) {
                            Button(action: { showModelPicker.toggle() }) {
                                Image(systemName: "xmark")
                            }
                        }
                    }
                #endif
            }
            #if os(iOS)
            .presentationDragIndicator(.visible)
            .if(appManager.userInterfaceIdiom == .phone) { view in
                view.presentationDetents([.fraction(0.4)])
            }
            #elseif os(macOS)
            .toolbar {
                ToolbarItem(placement: .destructiveAction) {
                    Button(action: { showModelPicker.toggle() }) {
                        Text("close")
                    }
                }
            }
            #endif
        }
        .sheet(isPresented: $showChats) {
            ChatsListView(currentThread: $currentThread, isPromptFocused: $isPromptFocused)
                #if os(iOS)
                .presentationDragIndicator(.visible)
                .if(appManager.userInterfaceIdiom == .phone) { view in
                    view.presentationDetents([.large])
                }
                #endif
        }
        .toolbar {
            #if os(iOS) || os(visionOS)
            ToolbarItem(placement: .topBarLeading) {
                Button(action: {
                    appManager.playHaptic()
                    showChats.toggle()
                }) {
                    Image(systemName: "list.bullet")
                }
            }

            ToolbarItem(placement: .topBarTrailing) {
                Button(action: {
                    appManager.playHaptic()
                    showSettings.toggle()
                }) {
                    Image(systemName: "gear")
                }
            }
            #elseif os(macOS)
            ToolbarItem(placement: .primaryAction) {
                Button(action: {
                    appManager.playHaptic()
                    showSettings.toggle()
                }) {
                    Label("settings", systemImage: "gear")
                }
            }
            #endif
        }
    }

    private func generate() {
        if !isPromptEmpty {
            if currentThread == nil {
                let newThread = Thread()
                currentThread = newThread
                modelContext.insert(newThread)
                try? modelContext.save()
            }

            if let currentThread = currentThread {
                generatingThreadID = currentThread.id
                Task {
                    let message = prompt
                    let imageData = selectedImageData
                    prompt = ""
                    selectedImageData = nil
                    selectedPhoto = nil
                    appManager.playHaptic()
                    sendMessage(Message(role: .user, content: message, thread: currentThread, imageData: imageData))
                    isPromptFocused = true
                    if let modelName = appManager.currentModelName {
                        let output = await llm.generate(modelName: modelName, thread: currentThread, systemPrompt: appManager.systemPrompt, imageData: imageData)
                        sendMessage(Message(role: .assistant, content: output, thread: currentThread, generatingTime: llm.thinkingTime))
                        generatingThreadID = nil
                    }
                }
            }
        }
    }

    private func sendMessage(_ message: Message) {
        appManager.playHaptic()
        modelContext.insert(message)
        try? modelContext.save()
    }

    #if os(macOS)
    private func handleShiftReturn() {
        if NSApp.currentEvent?.modifierFlags.contains(.shift) == true {
            prompt.append("\n")
            isPromptFocused = true
        } else {
            generate()
        }
    }
    #endif
}

#Preview {
    @FocusState var isPromptFocused: Bool
    ChatView(currentThread: .constant(nil), isPromptFocused: $isPromptFocused, showChats: .constant(false), showSettings: .constant(false))
}
