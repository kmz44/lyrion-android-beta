//
//  LanguageManager.swift
//  Lyrion
//
//  Language management for bilingual support
//

import SwiftUI

class LanguageManager: ObservableObject {
    @AppStorage("appLanguage") var appLanguage: String = Locale.current.language.languageCode?.identifier ?? "en"
    
    func changeLanguage(to language: String) {
        appLanguage = language
        UserDefaults.standard.set([language], forKey: "AppleLanguages")
        UserDefaults.standard.synchronize()
        
        // Notify the app to refresh
        objectWillChange.send()
    }
    
    func localizedString(_ key: String) -> String {
        let path = Bundle.main.path(forResource: appLanguage, ofType: "lproj")
        let bundle = path != nil ? Bundle(path: path!) : Bundle.main
        return NSLocalizedString(key, bundle: bundle ?? Bundle.main, comment: "")
    }
}
