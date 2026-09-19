<div align="center">
  <h1>🌌 Gemini NodeGuard</h1>
  <p><b>A high-performance V2Ray node validator for Google AI Services (Gemini & AI Studio)</b></p>
  
  [![Go Reference](https://pkg.go.dev/badge/golang.org/x/example.svg)](https://pkg.go.dev/golang.org/x/example)
  [![Go Report Card](https://goreportcard.com/badge/github.com/pouriyaahmadii/gemini-nodeguard)](https://goreportcard.com/report/github.com/pouriyaahmadii/gemini-nodeguard)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
</div>

---

## 📖 Overview

**Gemini NodeGuard** (formerly Gemini Sub Checker) is an automated Go utility designed for users who need reliable and uninterrupted connectivity to Google AI services (such as Gemini and Google AI Studio). It automatically fetches your V2Ray subscription links, parses the nodes, and actively probes them using `sing-box` or `xray` to ensure they can connect to Google AI endpoints without hitting annoying geoblocks or `HTTP 403 Forbidden` errors.

The final output is a clean, Base64-encoded subscription list that you can directly import into your favorite proxy clients (v2rayN, V2Box, Clash, etc.).

## ✨ Features

- 🔄 **Automated Parsing**: Fetches and decodes `vmess`, `vless`, `trojan`, `ss`, and `ssr` URIs.
- 🎯 **Precision Probing**: Verifies connectivity specifically against Google AI endpoints.
- 🚀 **High Concurrency**: Test hundreds of nodes in seconds using Go's lightweight goroutines.
- 🛠️ **Multi-Core Engines**: Supports both `sing-box` and `xray` cores as dialing engines.
- 📦 **Clean Base64 Export**: Generates standardized subscription files compatible with all major clients.
- 🤖 **CI/CD Ready**: Fully automated via GitHub Actions to continuously update and publish subscriptions.

---

## 🚀 Getting Started

### Prerequisites

- **Go 1.20+** installed on your local machine (if building/running locally).
- **sing-box** or **xray** binary in your system's `$PATH` or specified via CLI flags.

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/pouriyaahmadii/gemini-nodeguard.git
   cd gemini-nodeguard
   ```
2. Build the binary:
   ```bash
   go build -o gemini-nodeguard ./cmd/checker
   ```

### Local Usage

You can run the checker locally using command-line flags or a configuration JSON file.

```bash
./gemini-nodeguard -sub "https://your-sub-link.com/sub1,https://your-sub-link.com/sub2" -concurrency 20 -timeout 5s
```

#### Available CLI Flags:

| Flag | Description | Default |
| :--- | :--- | :--- |
| `-sub` | Comma-separated subscription URLs to fetch nodes from | `""` |
| `-targets` | Comma-separated target URLs to test (e.g., gemini.google.com) | Defined in config |
| `-config` | Path to a `config.json` file | `""` |
| `-out` | Output file path for the Gemini-compatible nodes | `output/gemini-sub.txt` |
| `-concurrency` | Number of concurrent worker threads | `10` |
| `-timeout` | Timeout duration for node testing (e.g., `8s`) | `5s` |
| `-singbox` | Custom path to the `sing-box` binary | auto-detected |
| `-xray` | Custom path to the `xray` binary | auto-detected |
| `-silent` | Suppress the banner and print only essential logs | `false` |

---

## ☁️ GitHub Actions Automation (Recommended)

You can run this project completely hands-free using GitHub Actions!

1. **Fork** this repository.
2. Navigate to your repository's **Settings > Secrets and variables > Actions**.
3. Create a new repository secret named `SUB_URLS`.
4. Paste your private subscription links (comma-separated if multiple) into the value.

The built-in GitHub workflow (`.github/workflows/check-and-publish.yml`) will run **every 4 hours**, test your nodes, and push the results to the `sub` branch of your repository.

### Subscribing to the Results

Once the pipeline finishes, you can add these raw links to your V2Ray client:

✅ **1. Gemini-Compatible Nodes**  
Contains ONLY the verified nodes that work with Google AI (prefixed with `[Gemini]`).
```text
https://raw.githubusercontent.com/<USERNAME>/<REPO>/sub/gemini-sub.txt
```

🌐 **2. General Nodes**  
Contains nodes that successfully work as a proxy but are geoblocked by Google AI (prefixed with `[General]`).
```text
https://raw.githubusercontent.com/<USERNAME>/<REPO>/sub/general-sub.txt
```

*(Replace `<USERNAME>` and `<REPO>` with your actual GitHub username and repository name)*

---

## 🏗️ Project Structure

```text
├── cmd/
│   └── checker/       # Main entry point for the CLI application
├── internal/
│   ├── banner/        # CLI ASCII banner generation
│   ├── checker/       # Core probing logic (sing-box/xray dialers)
│   ├── config/        # Configuration management
│   ├── exporter/      # Base64 encoding and file writing
│   ├── fetcher/       # HTTP fetching for subscription links
│   ├── parser/        # URI parsing for vmess, vless, trojan, etc.
│   └── types/         # Shared struct definitions
├── .github/workflows/ # CI/CD automation scripts
└── go.mod             # Go module dependencies
```

## 🤝 Contributing

Contributions are always welcome! Feel free to open an issue or submit a Pull Request if you have suggestions for improvements, new features, or bug fixes.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
