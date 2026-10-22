<div align="center">
  <h1>🛡️ Google Services NodeGuard</h1>
  <p><b>A high-performance, cross-platform, privacy-first proxy node validator for Google Services and AI Platforms</b></p>
  
  [![Go Version](https://img.shields.io/badge/Go-1.23.0+-00ADD8?logo=go&logoColor=white)](https://golang.org/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
</div>

---

## 📖 Overview

**Google Services NodeGuard** (formerly Gemini NodeGuard) is an extensible, privacy-first Go utility designed to validate proxy subscription nodes against a customizable registry of Google services.

Instead of a simple "works or doesn't work" ping, it analyzes the endpoints using `sing-box` or `xray`, parsing the HTTP responses to determine if a connection is genuinely eligible for services like Gemini, Google AI Studio, or NotebookLM, or if it is merely hitting an authentication prompt, geoblock, or a generic transport failure.

- 🚀 **Dual-Engine Core Support**: Seamless fallback between `sing-box` and `Xray` cores for maximum protocol coverage.
- 🎯 **Extensible Service Registry**: Configurable checking rules for Google Search, Gemini, AI Studio, NotebookLM, Jules, and Google AI API.
- 🔒 **Privacy-First Design**: Zero-trust architecture. No real subscription links, node credentials, or scan outputs are ever committed. The public CI uses strictly deterministic synthetic testing.
- 📦 **Segmented Plain-Text Outputs**: Dynamically exports nodes to cleanly separated files (`output/gemini-sub.txt`, `output/search-sub.txt`, `output/general-sub.txt`).

## ✨ Protocol Support Matrix

| Protocol    | Parsed | Validated | Xray Backend | Sing-box Backend | Reconstructed |
|-------------|--------|-----------|--------------|------------------|---------------|
| VLESS       | ✅     | ✅        | ✅           | ✅               | ✅            |
| VMess       | ✅     | ✅        | ✅           | ✅               | ✅            |
| Trojan      | ✅     | ✅        | ✅           | ✅               | ✅            |
| Shadowsocks | ✅     | ✅        | ✅           | ✅               | ✅ (SIP002 Base64)|

> Note: ShadowsocksR (`ssr`) is not currently supported due to compatibility deprecations with modern proxy engines.

## 🛠️ CLI Usage

```bash
git clone https://github.com/pouriyaahmadii/gemini-nodeguard.git
cd gemini-nodeguard
go build -o nodeguard cmd/checker/main.go
```

```bash
./nodeguard -sub "https://your-private-sub.url" -services "search,gemini,notebooklm" -concurrency 20
```

### Available CLI Flags:

| Flag          | Description                                                                     | Default                                |
| :------------ | :------------------------------------------------------------------------------ | :------------------------------------- |
| `-sub`        | Comma-separated subscription URLs to fetch nodes from                           | `""`                                   |
| `-services`   | Comma-separated list of services to test (`search,gemini,aistudio,notebooklm...`) | `search,gemini,aistudio,notebooklm,jules,ai_api` |
| `-out-dir`    | Directory path to save output subscriptions                                     | `output`                               |
| `-config`     | Path to a `config.json` file                                                    | `""`                                   |
| `-concurrency`| Number of concurrent worker threads                                             | `10`                                   |
| `-timeout`    | Timeout duration for node testing (e.g., `8s`)                                  | `8s`                                   |
| `-singbox`    | Custom path to the `sing-box` binary                                            | auto-detected                          |
| `-xray`       | Custom path to the `xray` binary                                                | auto-detected                          |
| `-silent`     | Suppress the banner and print only essential logs                               | `false`                                |

## 🛡️ Privacy and Safety Guarantees

This project enforces strict privacy defaults:
- **No Cloud Automation by Default:** We removed unsafe GitHub Actions workflows that automatically fetched and published your real private subscriptions to public GitHub Pages/Gists.
- **Local First:** Execution should be performed locally or on a private CI system you control.
- **`.gitignore` Enforced:** Output directories, binaries, and temporary configurations are globally excluded from tracking.
- **Data Redaction:** The checker will not print your proxy UUIDs, passwords, or full URIs in terminal logs.

## 📊 Result Classification Engine

The system classifies nodes using a structured model instead of a simple binary pass/fail:
1. `ConfirmedCompatible`: Endpoint explicitly allowed the connection.
2. `GeneralAlive`: Proxy transport works, but service compatibility failed.
3. `LikelyRegionRestricted`: The endpoint returned a known Geoblock pattern.
4. `AuthenticationRequired`: Service requires login.
5. `AccessDenied`: Permission denied (unrelated to location).
6. `RateLimited`: Too many requests (HTTP 429).
7. `ServiceError`: Google Server-side error (HTTP 500+).
8. `TransportError`: Proxy engine failed to dial or DNS failure.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.