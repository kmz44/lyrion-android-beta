//
//  ConversationView.swift
//  Lyrion
//
//  Created by Xavier on 16/12/2024.
//

import MarkdownUI
import SwiftUI
import WebKit

#if os(iOS)
import UIKit
#elseif os(macOS)
import AppKit
#endif

extension TimeInterval {
    var formatted: String {
        let totalSeconds = Int(self)
        let minutes = totalSeconds / 60
        let seconds = totalSeconds % 60

        if minutes > 0 {
            return seconds > 0 ? "\(minutes)m \(seconds)s" : "\(minutes)m"
        } else {
            return "\(seconds)s"
        }
    }
}

extension String {
    /// Detecta si el texto contiene fórmulas LaTeX
    var containsLaTeX: Bool {
        return contains("\\") || contains("$$") || contains("\\frac") || contains("\\pi") || contains("\\omega") || contains("\\alpha")
    }
    
    /// Prepara el texto para renderizado MathJax
    var preparedForMathJax: String {
        var result = self
        
        // Escapar contenido HTML pero preservar LaTeX
        result = result.replacingOccurrences(of: "<", with: "&lt;")
        result = result.replacingOccurrences(of: ">", with: "&gt;")
        
        // Restaurar tags de LaTeX
        result = result.replacingOccurrences(of: "&lt;think&gt;", with: "<think>")
        result = result.replacingOccurrences(of: "&lt;/think&gt;", with: "</think>")
        
        return result
    }
}

#if os(iOS)
struct MathJaxView: UIViewRepresentable {
    let content: String
    @Binding var dynamicHeight: CGFloat
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let contentController = WKUserContentController()
        config.userContentController = contentController
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        // PERMITIR scroll horizontal para fórmulas largas, pero NO vertical
        webView.scrollView.isScrollEnabled = true
        webView.scrollView.alwaysBounceVertical = false
        webView.scrollView.alwaysBounceHorizontal = true
        webView.scrollView.showsVerticalScrollIndicator = false
        webView.scrollView.showsHorizontalScrollIndicator = true
        webView.scrollView.bounces = false
        
        return webView
    }
    
    func updateUIView(_ webView: WKWebView, context: Context) {
        var htmlContent = content
        
        // PASO 1: Proteger expresiones LaTeX temporalmente
        var latexPlaceholders: [String: String] = [:]
        var placeholderIndex = 0
        
        // Proteger LaTeX display ($$...$$) - DEBE IR PRIMERO
        let displayLatexPattern = #"\$\$(.+?)\$\$"#
        if let regex = try? NSRegularExpression(pattern: displayLatexPattern, options: .dotMatchesLineSeparators) {
            let matches = regex.matches(in: htmlContent, range: NSRange(htmlContent.startIndex..., in: htmlContent))
            for match in matches.reversed() {
                if let range = Range(match.range, in: htmlContent) {
                    let placeholder = "___LATEX_DISPLAY_\(placeholderIndex)___"
                    latexPlaceholders[placeholder] = String(htmlContent[range])
                    htmlContent.replaceSubrange(range, with: placeholder)
                    placeholderIndex += 1
                }
            }
        }
        
        // Proteger LaTeX inline ($...$) - DESPUÉS de $$...$$
        // Patrón: $ + uno o más caracteres que NO sean $ ni salto de línea + $
        let inlineLatexPattern = #"\$[^\$\n]+\$"#
        if let regex = try? NSRegularExpression(pattern: inlineLatexPattern, options: []) {
            let matches = regex.matches(in: htmlContent, range: NSRange(htmlContent.startIndex..., in: htmlContent))
            for match in matches.reversed() {
                if let range = Range(match.range, in: htmlContent) {
                    let placeholder = "___LATEX_INLINE_\(placeholderIndex)___"
                    latexPlaceholders[placeholder] = String(htmlContent[range])
                    htmlContent.replaceSubrange(range, with: placeholder)
                    placeholderIndex += 1
                }
            }
        }
        
        // PASO 2: Procesar Markdown
        // Procesar línea por línea para encabezados
        let lines = htmlContent.components(separatedBy: .newlines)
        var processedLines: [String] = []
        
        for line in lines {
            var processedLine = line
            
            // Encabezados (###, ##, #)
            if processedLine.hasPrefix("### ") {
                processedLine = "<h3>" + processedLine.dropFirst(4) + "</h3>"
            } else if processedLine.hasPrefix("## ") {
                processedLine = "<h2>" + processedLine.dropFirst(3) + "</h2>"
            } else if processedLine.hasPrefix("# ") {
                processedLine = "<h1>" + processedLine.dropFirst(2) + "</h1>"
            }
            // Listas no ordenadas
            else if processedLine.hasPrefix("* ") || processedLine.hasPrefix("- ") {
                processedLine = "<li>" + processedLine.dropFirst(2) + "</li>"
            }
            
            processedLines.append(processedLine)
        }
        
        htmlContent = processedLines.joined(separator: "\n")
        
        // Procesar Markdown (ahora que LaTeX está protegido)
        // 1. Negritas con ** (debe ir ANTES que cursivas)
        // Patrón: captura texto entre ** que no contiene **
        while let range = htmlContent.range(of: #"\*\*([^*]+?)\*\*"#, options: .regularExpression) {
            let match = htmlContent[range]
            let content = match.dropFirst(2).dropLast(2)
            htmlContent.replaceSubrange(range, with: "<strong>\(content)</strong>")
        }
        
        // 2. Cursivas con * (después de negritas)
        // Patrón: captura texto entre * que no contiene *
        while let range = htmlContent.range(of: #"\*([^*]+?)\*"#, options: .regularExpression) {
            let match = htmlContent[range]
            let content = match.dropFirst(1).dropLast(1)
            htmlContent.replaceSubrange(range, with: "<em>\(content)</em>")
        }
        
        // 3. Código inline (`código`)
        while let range = htmlContent.range(of: #"`([^`]+)`"#, options: .regularExpression) {
            let match = htmlContent[range]
            let content = match.dropFirst(1).dropLast(1)
            htmlContent.replaceSubrange(range, with: "<code>\(content)</code>")
        }
        
        // Saltos de línea
        htmlContent = htmlContent.replacingOccurrences(of: "\n\n", with: "<br><br>")
        htmlContent = htmlContent.replacingOccurrences(of: "\n", with: "<br>")
        
        // PASO 3: Restaurar LaTeX
        for (placeholder, latex) in latexPlaceholders {
            htmlContent = htmlContent.replacingOccurrences(of: placeholder, with: latex)
        }
        
        let fullHTML = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">"
            <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
            <script src="https://polyfill.io/v3/polyfill.min.js?features=es6"></script>
            <script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            <script>
                window.MathJax = {
                    tex: {
                        inlineMath: [['$', '$'], ['\\\\(', '\\\\)']],
                        displayMath: [['$$', '$$'], ['\\\\[', '\\\\]']],
                        processEscapes: true,
                        processEnvironments: true
                    },
                    options: {
                        // Procesar LaTeX en TODOS los elementos HTML
                        skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
                    },
                    startup: {
                        ready: () => {
                            MathJax.startup.defaultReady();
                            MathJax.startup.promise.then(() => {
                                updateHeight();
                            });
                        }
                    }
                };
                
                function updateHeight() {
                    setTimeout(() => {
                        const height = Math.max(
                            document.body.scrollHeight,
                            document.body.offsetHeight,
                            document.documentElement.scrollHeight,
                            document.documentElement.offsetHeight
                        );
                        window.webkit.messageHandlers.heightUpdate.postMessage(height);
                    }, 100);
                }
                
                window.addEventListener('load', updateHeight);
                
                // Observar cambios en el DOM
                const observer = new MutationObserver(updateHeight);
                observer.observe(document.body, {
                    childList: true,
                    subtree: true,
                    attributes: true
                });
            </script>
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    font-size: 16px;
                    line-height: 1.6;
                    color: #000;
                    background: transparent;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    -webkit-text-size-adjust: none;
                    overflow-x: auto;
                    overflow-y: hidden;
                }
                body {
                    padding: 0;
                }
                /* Contenedor para fórmulas matemáticas con scroll horizontal */
                .mjx-container {
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    max-width: 100%;
                    display: block !important;
                }
                /* Fórmulas display ($$..$$) */
                mjx-container[display="true"] {
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    max-width: 100%;
                    display: block !important;
                    margin: 1em 0 !important;
                }
                /* Asegurar que las fórmulas largas no se corten */
                .MathJax {
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    max-width: 100%;
                }
                h1, h2, h3, h4, h5, h6 {
                    font-weight: 600;
                    margin: 1em 0 0.5em 0;
                    line-height: 1.3;
                }
                h1 { font-size: 1.8em; }
                h2 { font-size: 1.5em; }
                h3 { font-size: 1.3em; }
                h4 { font-size: 1.1em; }
                code {
                    background-color: rgba(128, 128, 128, 0.1);
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-family: 'SF Mono', Menlo, Consolas, monospace;
                    font-size: 0.9em;
                }
                li {
                    margin-left: 1.5em;
                    margin-bottom: 0.3em;
                }
                ul, ol {
                    margin: 0.5em 0;
                    padding-left: 1.5em;
                }
                p {
                    margin: 0.5em 0;
                }
                .mjx-math {
                    color: inherit;
                }
                .mjx-mrow {
                    color: inherit;
                }
                strong {
                    font-weight: 700;
                    color: inherit;
                }
                em {
                    font-style: italic;
                    color: inherit;
                }
                @media (prefers-color-scheme: dark) {
                    html, body {
                        color: #fff;
                    }
                    code {
                        background-color: rgba(255, 255, 255, 0.1);
                    }
                }
            </style>
        </head>
        <body>
            \(htmlContent)
        </body>
        </html>
        """
        
        webView.loadHTMLString(fullHTML, baseURL: nil)
    }
    
    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: MathJaxView
        var handlerAdded = false
        
        init(_ parent: MathJaxView) {
            self.parent = parent
        }
        
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            if !handlerAdded {
                webView.configuration.userContentController.add(self, name: "heightUpdate")
                handlerAdded = true
            }
        }
        
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if message.name == "heightUpdate" {
                if let height = message.body as? CGFloat {
                    DispatchQueue.main.async {
                        self.parent.dynamicHeight = max(height, 50)
                    }
                } else if let height = message.body as? Double {
                    DispatchQueue.main.async {
                        self.parent.dynamicHeight = max(CGFloat(height), 50)
                    }
                } else if let height = message.body as? Int {
                    DispatchQueue.main.async {
                        self.parent.dynamicHeight = max(CGFloat(height), 50)
                    }
                }
            }
        }
    }
}

// Wrapper para SwiftUI
struct MathJaxContentView: View {
    let content: String
    @State private var contentHeight: CGFloat = 100
    
    var body: some View {
        MathJaxView(content: content, dynamicHeight: $contentHeight)
            .frame(height: contentHeight)
    }
}
#elseif os(macOS)
// Version completa para macOS con NSViewRepresentable
struct MathJaxView: NSViewRepresentable {
    let content: String
    @Binding var dynamicHeight: CGFloat
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    func makeNSView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let contentController = WKUserContentController()
        config.userContentController = contentController
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.setValue(false, forKey: "drawsBackground")
        
        contentController.add(context.coordinator, name: "heightUpdate")
        
        return webView
    }
    
    func updateNSView(_ webView: WKWebView, context: Context) {
        var htmlContent = content
        
        // PASO 1: Proteger expresiones LaTeX temporalmente
        var latexPlaceholders: [String: String] = [:]
        var placeholderIndex = 0
        
        // Proteger LaTeX display ($$...$$) - DEBE IR PRIMERO
        let displayLatexPattern = #"\$\$(.+?)\$\$"#
        if let regex = try? NSRegularExpression(pattern: displayLatexPattern, options: .dotMatchesLineSeparators) {
            let matches = regex.matches(in: htmlContent, range: NSRange(htmlContent.startIndex..., in: htmlContent))
            for match in matches.reversed() {
                if let range = Range(match.range, in: htmlContent) {
                    let placeholder = "___LATEX_DISPLAY_\(placeholderIndex)___"
                    latexPlaceholders[placeholder] = String(htmlContent[range])
                    htmlContent.replaceSubrange(range, with: placeholder)
                    placeholderIndex += 1
                }
            }
        }
        
        // Proteger LaTeX inline ($...$) - DESPUÉS de $$...$$
        // Patrón: $ + uno o más caracteres que NO sean $ ni salto de línea + $
        let inlineLatexPattern = #"\$[^\$\n]+\$"#
        if let regex = try? NSRegularExpression(pattern: inlineLatexPattern, options: []) {
            let matches = regex.matches(in: htmlContent, range: NSRange(htmlContent.startIndex..., in: htmlContent))
            for match in matches.reversed() {
                if let range = Range(match.range, in: htmlContent) {
                    let placeholder = "___LATEX_INLINE_\(placeholderIndex)___"
                    latexPlaceholders[placeholder] = String(htmlContent[range])
                    htmlContent.replaceSubrange(range, with: placeholder)
                    placeholderIndex += 1
                }
            }
        }
        
        // PASO 2: Procesar Markdown
        // Procesar línea por línea para encabezados
        let lines = htmlContent.components(separatedBy: .newlines)
        var processedLines: [String] = []
        
        for line in lines {
            var processedLine = line
            
            // Encabezados (###, ##, #)
            if processedLine.hasPrefix("### ") {
                processedLine = "<h3>" + processedLine.dropFirst(4) + "</h3>"
            } else if processedLine.hasPrefix("## ") {
                processedLine = "<h2>" + processedLine.dropFirst(3) + "</h2>"
            } else if processedLine.hasPrefix("# ") {
                processedLine = "<h1>" + processedLine.dropFirst(2) + "</h1>"
            }
            // Listas no ordenadas
            else if processedLine.hasPrefix("* ") || processedLine.hasPrefix("- ") {
                processedLine = "<li>" + processedLine.dropFirst(2) + "</li>"
            }
            
            processedLines.append(processedLine)
        }
        
        htmlContent = processedLines.joined(separator: "\n")
        
        // Procesar Markdown (ahora que LaTeX está protegido)
        // 1. Negritas con ** (debe ir ANTES que cursivas)
        // Patrón: captura texto entre ** que no contiene **
        while let range = htmlContent.range(of: #"\*\*([^*]+?)\*\*"#, options: .regularExpression) {
            let match = htmlContent[range]
            let content = match.dropFirst(2).dropLast(2)
            htmlContent.replaceSubrange(range, with: "<strong>\(content)</strong>")
        }
        
        // 2. Cursivas con * (después de negritas)
        // Patrón: captura texto entre * que no contiene *
        while let range = htmlContent.range(of: #"\*([^*]+?)\*"#, options: .regularExpression) {
            let match = htmlContent[range]
            let content = match.dropFirst(1).dropLast(1)
            htmlContent.replaceSubrange(range, with: "<em>\(content)</em>")
        }
        
        // 3. Código inline (`código`)
        while let range = htmlContent.range(of: #"`([^`]+)`"#, options: .regularExpression) {
            let match = htmlContent[range]
            let content = match.dropFirst(1).dropLast(1)
            htmlContent.replaceSubrange(range, with: "<code>\(content)</code>")
        }
        
        // Saltos de línea
        htmlContent = htmlContent.replacingOccurrences(of: "\n\n", with: "<br><br>")
        htmlContent = htmlContent.replacingOccurrences(of: "\n", with: "<br>")
        
        // PASO 3: Restaurar LaTeX
        for (placeholder, latex) in latexPlaceholders {
            htmlContent = htmlContent.replacingOccurrences(of: placeholder, with: latex)
        }
        
        let fullHTML = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">"
            <meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
            <script src="https://polyfill.io/v3/polyfill.min.js?features=es6"></script>
            <script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
            <script>
                window.MathJax = {
                    tex: {
                        inlineMath: [['$', '$'], ['\\\\(', '\\\\)']],
                        displayMath: [['$$', '$$'], ['\\\\[', '\\\\]']],
                        processEscapes: true,
                        processEnvironments: true
                    },
                    options: {
                        // Procesar LaTeX en TODOS los elementos HTML
                        skipHtmlTags: ['script', 'noscript', 'style', 'textarea', 'pre', 'code']
                    },
                    startup: {
                        ready: () => {
                            MathJax.startup.defaultReady();
                            MathJax.startup.promise.then(() => {
                                updateHeight();
                            });
                        }
                    }
                };
                
                function updateHeight() {
                    setTimeout(() => {
                        const height = Math.max(
                            document.body.scrollHeight,
                            document.body.offsetHeight,
                            document.documentElement.scrollHeight,
                            document.documentElement.offsetHeight
                        );
                        window.webkit.messageHandlers.heightUpdate.postMessage(height);
                    }, 100);
                }
                
                window.addEventListener('load', updateHeight);
                
                // Observar cambios en el DOM
                const observer = new MutationObserver(updateHeight);
                observer.observe(document.body, {
                    childList: true,
                    subtree: true,
                    attributes: true
                });
            </script>
            <style>
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    font-size: 16px;
                    line-height: 1.6;
                    color: #000;
                    background: transparent;
                    word-wrap: break-word;
                    overflow-wrap: break-word;
                    -webkit-text-size-adjust: none;
                    overflow-x: auto;
                    overflow-y: hidden;
                }
                body {
                    padding: 0;
                }
                /* Contenedor para fórmulas matemáticas con scroll horizontal */
                .mjx-container {
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    max-width: 100%;
                    display: block !important;
                }
                /* Fórmulas display ($$..$$) */
                mjx-container[display="true"] {
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    max-width: 100%;
                    display: block !important;
                    margin: 1em 0 !important;
                }
                /* Asegurar que las fórmulas largas no se corten */
                .MathJax {
                    overflow-x: auto !important;
                    overflow-y: hidden !important;
                    max-width: 100%;
                }
                h1, h2, h3, h4, h5, h6 {
                    font-weight: 600;
                    margin: 1em 0 0.5em 0;
                    line-height: 1.3;
                }
                h1 { font-size: 1.8em; }
                h2 { font-size: 1.5em; }
                h3 { font-size: 1.3em; }
                h4 { font-size: 1.1em; }
                code {
                    background-color: rgba(128, 128, 128, 0.1);
                    padding: 2px 6px;
                    border-radius: 4px;
                    font-family: 'SF Mono', Menlo, Consolas, monospace;
                    font-size: 0.9em;
                }
                li {
                    margin-left: 1.5em;
                    margin-bottom: 0.3em;
                }
                ul, ol {
                    margin: 0.5em 0;
                    padding-left: 1.5em;
                }
                p {
                    margin: 0.5em 0;
                }
                .mjx-math {
                    color: inherit;
                }
                .mjx-mrow {
                    color: inherit;
                }
                strong {
                    font-weight: 700;
                    color: inherit;
                }
                em {
                    font-style: italic;
                    color: inherit;
                }
                @media (prefers-color-scheme: dark) {
                    html, body {
                        color: #fff;
                    }
                    code {
                        background-color: rgba(255, 255, 255, 0.1);
                    }
                }
            </style>
        </head>
        <body>
            \(htmlContent)
        </body>
        </html>
        """
        
        webView.loadHTMLString(fullHTML, baseURL: nil)
    }
    
    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: MathJaxView
        
        init(_ parent: MathJaxView) {
            self.parent = parent
        }
        
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if message.name == "heightUpdate" {
                if let height = message.body as? CGFloat {
                    DispatchQueue.main.async {
                        self.parent.dynamicHeight = max(height, 50)
                    }
                } else if let height = message.body as? Double {
                    DispatchQueue.main.async {
                        self.parent.dynamicHeight = max(CGFloat(height), 50)
                    }
                } else if let height = message.body as? Int {
                    DispatchQueue.main.async {
                        self.parent.dynamicHeight = max(CGFloat(height), 50)
                    }
                }
            }
        }
    }
}

// Wrapper para SwiftUI en macOS
struct MathJaxContentView: View {
    let content: String
    @State private var contentHeight: CGFloat = 100
    
    var body: some View {
        MathJaxView(content: content, dynamicHeight: $contentHeight)
            .frame(height: contentHeight)
    }
}
#else
// Fallback para otras plataformas
struct MathJaxContentView: View {
    let content: String
    
    var body: some View {
        Text(content)
            .textSelection(.enabled)
    }
}
#endif

struct MessageView: View {
    @Environment(LLMEvaluator.self) var llm
    @State private var collapsed = true
    @State private var showCopyConfirmation = false
    let message: Message

    var isThinking: Bool {
        message.content.contains("<think>") && !message.content.contains("</think>")
    }

    func processThinkingContent(_ content: String) -> (String?, String?) {
        // Si no hay ningún tag de <think>, retornar todo el contenido como respuesta normal
        guard let startRange = content.range(of: "<think>") else {
            return (nil, content.trimmingCharacters(in: .whitespacesAndNewlines))
        }
        
        // Buscar el tag de cierre </think>
        guard let endRange = content.range(of: "</think>", options: [], range: startRange.upperBound..<content.endIndex) else {
            // No hay tag de cierre pero sí hay apertura - aún está pensando
            let thinking = String(content[startRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            return (thinking, nil)
        }

        // Extraer el contenido de pensamiento (entre <think> y </think>)
        let thinking = String(content[startRange.upperBound ..< endRange.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        
        // Extraer el contenido después del pensamiento (después de </think>)
        let afterThink = String(content[endRange.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)

        return (thinking.isEmpty ? nil : thinking, afterThink.isEmpty ? nil : afterThink)
    }

    var time: String {
        if isThinking, llm.running, let elapsedTime = llm.elapsedTime {
            if isThinking {
                return "(\(elapsedTime.formatted))"
            }
            if let thinkingTime = llm.thinkingTime {
                return thinkingTime.formatted
            }
        } else if let generatingTime = message.generatingTime {
            return "\(generatingTime.formatted)"
        }

        return "0s"
    }

    var thinkingLabel: some View {
        HStack {
            Button {
                collapsed.toggle()
            } label: {
                Image(systemName: collapsed ? "chevron.right" : "chevron.down")
                    .font(.system(size: 12))
                    .fontWeight(.medium)
            }

            Text("\(NSLocalizedString(isThinking ? "conversation.thinking" : "conversation.thought_for", comment: "")) \(time)")
                .italic()
        }
        .buttonStyle(.borderless)
        .foregroundStyle(.secondary)
    }
    
    func copyToClipboard(_ text: String) {
        #if os(iOS)
        UIPasteboard.general.string = text
        #elseif os(macOS)
        NSPasteboard.general.clearContents()
        NSPasteboard.general.setString(text, forType: .string)
        #endif
        showCopyConfirmation = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
            showCopyConfirmation = false
        }
    }

    var body: some View {
        HStack {
            if message.role == .user { Spacer() }

            if message.role == .assistant {
                let (thinking, afterThink) = processThinkingContent(message.content)
                VStack(alignment: .leading, spacing: 16) {
                    if let thinking {
                        VStack(alignment: .leading, spacing: 12) {
                            thinkingLabel
                            if !collapsed {
                                if !thinking.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                                    HStack(spacing: 12) {
                                        Capsule()
                                            .frame(width: 2)
                                            .padding(.vertical, 1)
                                            .foregroundStyle(.fill)
                                        if thinking.containsLaTeX {
                                            MathJaxContentView(content: thinking)
                                        } else {
                                            Markdown(thinking)
                                                .textSelection(.enabled)
                                                .markdownTextStyle {
                                                    ForegroundColor(.secondary)
                                                }
                                        }
                                    }
                                    .padding(.leading, 5)
                                    .padding(.leading, 5)
                                }
                            }
                        }
                        .contentShape(.rect)
                        .onTapGesture {
                            collapsed.toggle()
                            if isThinking {
                                llm.collapsed = collapsed
                            }
                        }
                    }

                    if let afterThink {
                        VStack(alignment: .leading, spacing: 8) {
                            if afterThink.containsLaTeX {
                                MathJaxContentView(content: afterThink)
                            } else {
                                Markdown(afterThink)
                                    .textSelection(.enabled)
                            }
                            
                            // Botón de copiar para respuestas de la IA
                            HStack {
                                Button(action: {
                                    copyToClipboard(afterThink)
                                }) {
                                    HStack(spacing: 4) {
                                        Image(systemName: showCopyConfirmation ? "checkmark" : "doc.on.doc")
                                            .font(.system(size: 12))
                                        Text(showCopyConfirmation ? "Copiado" : "Copiar")
                                            .font(.system(size: 12))
                                    }
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Color.secondary.opacity(0.1))
                                    .cornerRadius(8)
                                }
                                .buttonStyle(.borderless)
                                .foregroundStyle(.secondary)
                                
                                Spacer()
                            }
                            .padding(.top, 4)
                        }
                    }
                }
                .padding(.trailing, 48)
            } else {
                VStack(alignment: .trailing, spacing: 8) {
                    // Display image if present
                    #if os(iOS)
                    if let imageData = message.imageData, let uiImage = UIImage(data: imageData) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(maxWidth: 200, maxHeight: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    #elseif os(macOS)
                    if let imageData = message.imageData, let nsImage = NSImage(data: imageData) {
                        Image(nsImage: nsImage)
                            .resizable()
                            .scaledToFill()
                            .frame(maxWidth: 200, maxHeight: 200)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    #endif
                    
                    Group {
                        if message.content.containsLaTeX {
                            MathJaxContentView(content: message.content)
                        #if os(iOS) || os(visionOS)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 12)
                        #else
                                .padding(.horizontal, 16 * 2 / 3)
                                .padding(.vertical, 8)
                        #endif
                        } else {
                            Markdown(message.content)
                                .textSelection(.enabled)
                        #if os(iOS) || os(visionOS)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 12)
                        #else
                                .padding(.horizontal, 16 * 2 / 3)
                                .padding(.vertical, 8)
                        #endif
                        }
                    }
                    .background(platformBackgroundColor)
                    #if os(iOS) || os(visionOS)
                        .mask(RoundedRectangle(cornerRadius: 24))
                    #elseif os(macOS)
                        .mask(RoundedRectangle(cornerRadius: 16))
                    #endif
                }
                .padding(.leading, 48)
            }

            if message.role == .assistant { Spacer() }
        }
        .onAppear {
            if llm.running {
                collapsed = false
            }
        }
        .onChange(of: llm.elapsedTime) {
            if isThinking {
                llm.thinkingTime = llm.elapsedTime
            }
        }
        .onChange(of: isThinking) {
            if llm.running {
                llm.isThinking = isThinking
            }
        }
    }

    var platformBackgroundColor: Color {
        #if os(iOS)
        return Color(UIColor.tertiarySystemBackground)
        #elseif os(visionOS)
        return Color(UIColor.separator)
        #elseif os(macOS)
        return Color(NSColor.secondarySystemFill)
        #endif
    }
}

struct ConversationView: View {
    @Environment(LLMEvaluator.self) var llm
    @EnvironmentObject var appManager: AppManager
    let thread: Thread
    let generatingThreadID: UUID?

    @State private var scrollInterrupted = false
    @State private var hasScrolledToBottom = false
    @State private var autoScrollEnabled = true

    var body: some View {
        ScrollViewReader { scrollView in
            ScrollView(.vertical, showsIndicators: true) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(thread.sortedMessages) { message in
                        MessageView(message: message)
                            .padding()
                            .id(message.id.uuidString)
                    }

                    if llm.running && !llm.output.isEmpty && thread.id == generatingThreadID {
                        VStack {
                            MessageView(message: Message(role: .assistant, content: llm.output + " 📝"))
                        }
                        .padding()
                        .id("output")
                        .onAppear {
                            print("output appeared")
                            scrollInterrupted = false
                            autoScrollEnabled = true // Re-enable auto scroll for new generation
                        }
                    }

                    Rectangle()
                        .fill(.clear)
                        .frame(height: 1)
                        .id("bottom")
                }
            }
            #if os(macOS)
            .scrollBounceBehavior(.basedOnSize)
            .simultaneousGesture(
                DragGesture(minimumDistance: 10)
                    .onChanged { _ in
                        // User is manually scrolling, disable auto-scroll
                        if llm.running {
                            autoScrollEnabled = false
                            scrollInterrupted = true
                        }
                    }
            )
            #endif
            .onAppear {
                // Scroll to bottom only when view first appears
                if !hasScrolledToBottom {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                        scrollView.scrollTo("bottom", anchor: .bottom)
                        hasScrolledToBottom = true
                    }
                }
            }
            .onChange(of: thread.id) { _, _ in
                // Reset flags when switching threads
                hasScrolledToBottom = false
                autoScrollEnabled = true
                scrollInterrupted = false
            }
            .onChange(of: llm.running) { _, isRunning in
                // Reset auto-scroll when generation stops
                if !isRunning {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        autoScrollEnabled = true
                        scrollInterrupted = false
                    }
                }
            }
            .onChange(of: llm.output) { _, _ in
                // auto scroll to bottom only during generation and if not interrupted
                if llm.running && autoScrollEnabled && !scrollInterrupted {
                    #if os(macOS)
                    scrollView.scrollTo("bottom", anchor: .bottom)
                    #else
                    scrollView.scrollTo("bottom", anchor: .bottom)
                    #endif
                }

                if !llm.isThinking {
                    appManager.playHaptic()
                }
            }
        }
        #if os(iOS)
        .scrollDismissesKeyboard(.interactively)
        #endif
    }
}

#Preview {
    ConversationView(thread: Thread(), generatingThreadID: nil)
        .environment(LLMEvaluator())
        .environmentObject(AppManager())
}
