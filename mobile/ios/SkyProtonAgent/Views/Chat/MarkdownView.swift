import SwiftUI
import WebKit

// MARK: – Weak handler wrapper (avoids WKUserContentController retain cycle)

private class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ target: WKScriptMessageHandler) { self.target = target }
    func userContentController(_ uc: WKUserContentController, didReceive msg: WKScriptMessage) {
        target?.userContentController(uc, didReceive: msg)
    }
}

// MARK: – UIViewRepresentable

struct MarkdownView: UIViewRepresentable {
    let content: String
    let isDark: Bool
    @Binding var height: CGFloat
    var onHtmlPreview: ((String) -> Void)?

    func makeCoordinator() -> Coordinator { Coordinator($height) }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        let uc = WKUserContentController()
        uc.add(WeakMessageHandler(context.coordinator), name: "heightChanged")
        uc.add(WeakMessageHandler(context.coordinator), name: "htmlPreview")
        config.userContentController = uc
        config.preferences.setValue(true, forKey: "allowFileAccessFromFileURLs")

        let wv = WKWebView(frame: .zero, configuration: config)
        wv.isOpaque = false
        wv.backgroundColor = .clear
        wv.scrollView.isScrollEnabled = false
        wv.scrollView.bounces = false
        return wv
    }

    func updateUIView(_ wv: WKWebView, context: Context) {
        let c = context.coordinator
        c.onHtmlPreview = onHtmlPreview
        guard c.lastContent != content || c.isDark != isDark else { return }
        c.lastContent = content
        c.isDark = isDark
        guard let jsonData = try? JSONEncoder().encode(content),
              let json = String(data: jsonData, encoding: .utf8) else { return }
        let html = Self.pageHTML(json: json, dark: isDark)
        wv.loadHTMLString(html, baseURL: URL(string: "https://cdn.jsdelivr.net"))
    }

    // MARK: – Coordinator

    class Coordinator: NSObject, WKScriptMessageHandler {
        @Binding var height: CGFloat
        var lastContent: String = ""
        var isDark: Bool = false
        var onHtmlPreview: ((String) -> Void)?

        init(_ h: Binding<CGFloat>) { _height = h }

        func userContentController(_ uc: WKUserContentController, didReceive msg: WKScriptMessage) {
            if msg.name == "heightChanged" {
                let h: CGFloat
                if let d = msg.body as? Double      { h = CGFloat(d) }
                else if let i = msg.body as? Int    { h = CGFloat(i) }
                else { return }
                DispatchQueue.main.async { if abs(self.height - h) > 1 { self.height = max(h, 16) } }
            } else if msg.name == "htmlPreview", let src = msg.body as? String {
                DispatchQueue.main.async { self.onHtmlPreview?(src) }
            }
        }
    }

    // MARK: – HTML template

    static func pageHTML(json: String, dark: Bool) -> String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
        <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.css">
        <style>
          :root {
            --fg:      \(dark ? "#f2f2f7" : "#1c1c1e");
            --muted:   \(dark ? "#8e8e93" : "#6d6d72");
            --code-bg: \(dark ? "rgba(255,255,255,0.09)" : "rgba(0,0,0,0.06)");
            --border:  \(dark ? "rgba(255,255,255,0.14)" : "rgba(0,0,0,0.12)");
            --link:    \(dark ? "#64d2ff" : "#007aff");
            --pre-bg:  \(dark ? "#1c1c1e" : "#f2f2f7");
          }
          * { box-sizing: border-box; margin: 0; padding: 0; }
          html, body {
            background: transparent;
            color: var(--fg);
            font-family: -apple-system, 'SF Pro Text', sans-serif;
            font-size: 15px;
            line-height: 1.6;
            overflow: hidden;
            word-wrap: break-word;
          }
          #md { padding: 1px 0; }
          p { margin: 0.35em 0; }
          p:first-child { margin-top: 0; }
          p:last-child  { margin-bottom: 0; }
          h1 { font-size: 1.25em; font-weight: 700; margin: 0.75em 0 0.3em; }
          h2 { font-size: 1.1em;  font-weight: 600; margin: 0.65em 0 0.25em; }
          h3 { font-size: 1em;    font-weight: 600; margin: 0.55em 0 0.2em; }
          h4, h5, h6 { font-size: 0.95em; font-weight: 600; margin: 0.45em 0 0.15em; }
          ul, ol { padding-left: 1.4em; margin: 0.3em 0; }
          li     { margin: 0.15em 0; }
          ul ul, ol ol, ul ol, ol ul { margin: 0.1em 0; }
          strong { font-weight: 600; }
          em     { font-style: italic; }
          del    { text-decoration: line-through; opacity: 0.7; }
          code {
            font-family: 'SF Mono', 'Menlo', monospace;
            font-size: 0.82em;
            background: var(--code-bg);
            padding: 0.12em 0.32em;
            border-radius: 4px;
          }
          pre {
            background: var(--pre-bg);
            border-radius: 8px;
            padding: 10px 12px;
            overflow-x: auto;
            margin: 0.5em 0;
          }
          pre code { background: none; padding: 0; font-size: 0.81em; }
          blockquote {
            border-left: 3px solid var(--border);
            padding: 0.15em 0.65em;
            color: var(--muted);
            margin: 0.4em 0;
          }
          table { border-collapse: collapse; width: 100%; margin: 0.5em 0; font-size: 0.9em; }
          th, td { border: 1px solid var(--border); padding: 5px 8px; text-align: left; }
          th { font-weight: 600; }
          a { color: var(--link); text-decoration: none; }
          hr { border: none; border-top: 1px solid var(--border); margin: 0.7em 0; }
          .katex-display { overflow-x: auto; padding: 4px 0; text-align: center; }
          .html-preview-bar {
            display: flex; align-items: center; justify-content: space-between;
            background: #111; padding: 6px 12px; border-radius: 6px 6px 0 0;
          }
          .html-preview-bar span { font-size: 10px; font-weight: 600; color: #888; letter-spacing: 0.05em; text-transform: uppercase; }
          .html-preview-bar button {
            font-size: 11px; color: #60a5fa; background: none; border: none; cursor: pointer;
            font-weight: 600; padding: 4px 8px;
          }
          .html-code { background: #111; border-radius: 0 0 6px 6px; padding: 10px 12px; overflow-x: auto; margin-bottom: 0.5em; }
          .html-code code { color: #f0f0f0; font-size: 0.8em; }
        </style>
        </head>
        <body>
        <div id="md"></div>
        <script src="https://cdn.jsdelivr.net/npm/marked@12.0.0/marked.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/katex.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/katex@0.16.9/dist/contrib/auto-render.min.js"></script>
        <script>
        var _htmlSources = [];

        function reportHeight() {
          var h = document.getElementById('md').offsetHeight;
          window.webkit.messageHandlers.heightChanged.postMessage(h);
        }

        function openHtmlPreview(idx) {
          window.webkit.messageHandlers.htmlPreview.postMessage(_htmlSources[idx]);
        }

        function render() {
          var src = \(json);
          marked.setOptions({ gfm: true, breaks: true });

          // marked v12: renderer methods receive a token object, not positional args
          var renderer = {
            code: function(token) {
              var text = (token && token.text != null) ? token.text : String(token);
              var lang = (token && token.lang) ? token.lang : '';
              var escaped = text.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
              if (lang === 'html' || lang === 'HTML') {
                var idx = _htmlSources.length;
                _htmlSources.push(text);
                return '<div class="html-preview-bar"><span>HTML</span>' +
                       '<button onclick="openHtmlPreview(' + idx + ')">Preview</button></div>' +
                       '<div class="html-code"><pre><code>' + escaped + '</code></pre></div>';
              }
              return '<pre><code>' + escaped + '</code></pre>';
            }
          };
          marked.use({ renderer: renderer });

          document.getElementById('md').innerHTML = marked.parse(src);
          if (typeof renderMathInElement !== 'undefined') {
            renderMathInElement(document.getElementById('md'), {
              delimiters: [
                { left: '$$', right: '$$', display: true  },
                { left: '$',  right: '$',  display: false },
                { left: '\\\\(', right: '\\\\)', display: false },
                { left: '\\\\[', right: '\\\\]', display: true  }
              ],
              throwOnError: false
            });
          }
          setTimeout(reportHeight, 60);
        }

        function tryRender() {
          if (typeof marked !== 'undefined') {
            render();
          } else {
            setTimeout(tryRender, 50);
          }
        }

        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', tryRender);
        } else {
          tryRender();
        }
        window.addEventListener('resize', reportHeight);
        </script>
        </body>
        </html>
        """
    }
}

// MARK: – SwiftUI wrapper with height tracking

struct MarkdownBubble: View {
    let content: String
    // When provided, the caller owns HTML preview presentation (e.g. when already inside a sheet).
    // When nil, MarkdownBubble presents its own sheet (default chat usage).
    var onHtmlPreview: ((String) -> Void)? = nil
    @Environment(\.colorScheme) private var scheme
    @State private var height: CGFloat = 60
    @State private var showHtmlPreview = false
    @State private var htmlPreviewSrc = ""

    var body: some View {
        MarkdownView(
            content: content,
            isDark: scheme == .dark,
            height: $height,
            onHtmlPreview: { src in
                if let external = onHtmlPreview {
                    external(src)
                } else {
                    htmlPreviewSrc = src
                    showHtmlPreview = true
                }
            }
        )
        .frame(height: height)
        .animation(.easeInOut(duration: 0.15), value: height)
        .sheet(isPresented: $showHtmlPreview) {
            HtmlPreviewSheet(source: htmlPreviewSrc)
        }
    }
}

// MARK: – HTML preview sheet

struct HtmlPreviewSheet: View {
    let source: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            HtmlPreviewWebView(html: source)
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle("HTML Preview")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Done") { dismiss() }
                    }
                }
        }
    }
}

struct HtmlPreviewWebView: UIViewRepresentable {
    let html: String

    func makeUIView(context: Context) -> WKWebView { WKWebView() }

    func updateUIView(_ webView: WKWebView, context: Context) {
        webView.loadHTMLString(html, baseURL: nil)
    }
}
