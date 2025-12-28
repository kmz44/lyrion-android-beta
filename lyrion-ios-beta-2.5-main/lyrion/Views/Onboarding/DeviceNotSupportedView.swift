//
//  DeviceNotSupportedView.swift
//  Lyrion
//
//  Created by Xavier on 10/12/2024.
//

import SwiftUI

struct DeviceNotSupportedView: View {
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "iphone.slash")
                .font(.system(size: 64))
                .foregroundStyle(.primary, .tertiary)
            
            VStack(spacing: 4) {
                Text(NSLocalizedString("onboarding.device_not_supported.title", comment: "Device not supported title"))
                    .font(.title)
                    .fontWeight(.semibold)
                Text(NSLocalizedString("onboarding.device_not_supported.message", comment: "Device not supported message"))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding()
    }
}

#Preview {
    DeviceNotSupportedView()
}
