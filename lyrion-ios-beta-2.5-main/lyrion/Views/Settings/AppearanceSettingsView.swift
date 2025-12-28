//
//  AppearanceSettingsView.swift
//  Lyrion
//
//  Created by Jordan Singer on 10/5/24.
//

import SwiftUI

struct AppearanceSettingsView: View {
    @EnvironmentObject var appManager: AppManager

    var body: some View {
        Form {
            #if os(iOS)
            Section {
                Picker(selection: $appManager.appTintColor) {
                    ForEach(AppTintColor.allCases.sorted(by: { $0.rawValue < $1.rawValue }), id: \.rawValue) { option in
                        Text(String(describing: option).lowercased())
                            .tag(option)
                    }
                } label: {
                    Label(NSLocalizedString("appearance.color", comment: ""), systemImage: "paintbrush.pointed")
                }
            }
            #endif

            Section(header: Text(NSLocalizedString("appearance.font", comment: ""))) {
                Picker(selection: $appManager.appFontDesign) {
                    ForEach(AppFontDesign.allCases.sorted(by: { $0.rawValue < $1.rawValue }), id: \.rawValue) { option in
                        Text(String(describing: option).lowercased())
                            .tag(option)
                    }
                } label: {
                    Label(NSLocalizedString("appearance.design", comment: ""), systemImage: "textformat")
                }

                Picker(selection: $appManager.appFontWidth) {
                    ForEach(AppFontWidth.allCases.sorted(by: { $0.rawValue < $1.rawValue }), id: \.rawValue) { option in
                        Text(String(describing: option).lowercased())
                            .tag(option)
                    }
                } label: {
                    Label(NSLocalizedString("appearance.width", comment: ""), systemImage: "arrow.left.and.line.vertical.and.arrow.right")
                }
                .disabled(appManager.appFontDesign != .standard)

                #if !os(macOS)
                Picker(selection: $appManager.appFontSize) {
                    ForEach(AppFontSize.allCases.sorted(by: { $0.rawValue < $1.rawValue }), id: \.rawValue) { option in
                        Text(String(describing: option).lowercased())
                            .tag(option)
                    }
                } label: {
                    Label(NSLocalizedString("appearance.size", comment: ""), systemImage: "arrow.up.left.and.arrow.down.right")
                }
                #endif
            }
        }
        .formStyle(.grouped)
        .navigationTitle(NSLocalizedString("appearance.title", comment: ""))
        #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
        #endif
    }
}

#Preview {
    AppearanceSettingsView()
}
