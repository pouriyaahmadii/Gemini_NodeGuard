<div align="center">
  <h1>🌌 Gemini NodeGuard</h1>
  <p><b>A high-performance V2Ray node validator for Google AI Services (Gemini & AI Studio)</b></p>
  
  [![Go Version](https://img.shields.io/badge/Go-1.22+-00ADD8?logo=go&logoColor=white)](https://golang.org/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
</div>

---

## 📖 Overview

**Gemini NodeGuard** (formerly Gemini Sub Checker) is an automated Go utility designed for users who need reliable and uninterrupted connectivity to Google AI services (such as Gemini and Google AI Studio). It automatically fetches your private proxy subscription links, parses the nodes, and actively probes them using `sing-box` or `xray` to ensure they can connect to Google AI endpoints without hitting annoying geoblocks or authentication false negatives (`HTTP 403 Forbidden`).

The final output is a clean, Base64-encoded subscription list that you can directly import into your favorite proxy clients (v2rayN, V2Box, Clash, etc.).

- 🚀 **Dual-Engine Core Support**: Seamless fallback between `sing-box` and `Xray` cores for maximum protocol coverage, including VLESS, VMess, Reality, Shadowsocks, and Trojan.
- 🎯 **Dedicated Gemini Probing**: Validates connectivity against `gemini.google.com`, `jules.google.com`, and `generativelanguage.googleapis.com` with smart status-code handling.
- 🌍 **Cross-Platform Support**: Works seamlessly across Windows, macOS, and Linux via CLI, alongside a dedicated Windows Desktop GUI.
- 📦 **Segmented Plain-Text Outputs**: Exports nodes as clean, readable configurations in:
  - `output/gemini-sub.txt`: Nodes verified to unlock Gemini and Google AI.
  - `output/general-sub.txt`: General alive nodes for everyday web browsing.
- 🔒 **Privacy-First Design**: No proxies or subscription credentials are ever committed to public git branches.

## ✨ Features

- 🔄 **Automated Parsing**: Fetches and decodes `vmess`, `vless`, `trojan`, `ss`, and `ssr` URIs.
- 🎯 **Precision Probing**: Verifies connectivity specifically against Google AI endpoints.
- 🚀 **High Concurrency**: Test hundreds of nodes in seconds using Go's lightweight goroutines.
- 🛠️ **Multi-Core Engines**: Supports both `sing-box` and `xray` cores as dialing engines.
- 🌍 **Cross-Platform**: Native binaries for Windows, macOS, and Linux.
- 📦 **Clean Base64 Export**: Generates standardized subscription files compatible with all major clients.
- 🤖 **CI/CD Ready**: Fully automated via GitHub Actions to continuously update and publish subscriptions.

---

## ☁️ GitHub Actions Automation (Recommended)

You can run this project completely hands-free using GitHub Actions!

### Step 1: Fork this repository
Click the **Fork** button at the top right of this page to create your own copy of the project.

### Step 2: Configure Subscription Links
1. Go to your forked repository's **Settings > Secrets and variables > Actions**.
2. Click **New repository secret**.
3. Name it `SUB_URLS`.
4. Paste your private subscription links (comma-separated if you have multiple) into the value field.

### Step 3: Run the Workflow
1. Navigate to the **Actions** tab in your repository.
2. Select the **Check and Publish Subscriptions** workflow on the left sidebar.
3. Click the **Run workflow** dropdown on the right and trigger it.

The built-in GitHub workflow (`.github/workflows/check-and-publish.yml`) will also run automatically **every 4 hours**, test your nodes, and push the results to the `sub` branch of your repository.

### Step 4: Retrieve Your Verified Nodes
Once the pipeline finishes, you have two ways to retrieve your nodes:

#### Option A: Direct Download (Default & Easiest)
1. Open the successful workflow run.
2. Scroll down to the **Artifacts** section at the bottom.
3. Download the `validated-nodes.zip` file.
4. Extract the `.txt` files and import them directly into your client (V2RayNG, Sing-box, Hiddify, etc.).

#### Option B: Private Gist Subscription (Advanced)
If you prefer an auto-updating live URL for your client:
1. Create a **Secret Gist** on GitHub and note its ID (the string in the URL).
2. Generate a GitHub Personal Access Token (classic) with `gist` permissions.
3. In your repository's **Settings > Secrets and variables > Actions**, add:
   - `GIST_TOKEN`: Your personal access token.
   - `GIST_ID`: The ID of the secret Gist you created.
4. The GitHub Action will now automatically update your Secret Gist, giving you a private, live URL to subscribe to in your proxy client!

#### Option C: Subscribing to the Results (Public Repository)
If your repository is public, you can add these raw links to your V2Ray client:

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

> [!NOTE]
> Always make sure to replace `<USERNAME>` and `<REPO>` in the URL with your own GitHub username and repository name if you have forked this project.

---

## 💻 Local Usage

### Prerequisites
- **Go 1.22+** installed on your local machine (if building/running locally).
- **sing-box** or **xray** binary in your system's `$PATH` or specified via CLI flags.

### 1. Windows Desktop GUI
Download the pre-compiled `gemini-nodeguard-gui-windows-amd64.exe` from the [Releases](https://github.com/pouriyaahmadii/gemini-nodeguard/releases) page.
- Double click to launch the application.
- Paste your subscription URLs, click start, and watch the real-time status log!

### 2. Go CLI
If you prefer the command line, make sure you have Go 1.22+ installed:
```bash
git clone https://github.com/pouriyaahmadii/gemini-nodeguard.git
cd gemini-nodeguard
go build -o gemini-nodeguard cmd/checker/main.go
```

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

## 🏗️ Project Structure

```text
├── cmd/
│   └── checker/
│       └── main.go                  # Main entry point for the CLI application
├── internal/
│   ├── banner/                      # CLI ASCII banner generation
│   ├── checker/                     # Core probing logic, dialers (sing-box/xray), and downloader
│   ├── config/                      # Configuration management and config structs
│   ├── exporter/                    # Base64 encoding, formatting, and file writing
│   ├── fetcher/                     # HTTP fetching for remote subscription links
│   ├── parser/                      # URI parsing logic (vmess, vless, trojan, ss, ssr)
│   └── types/                       # Shared models and struct definitions
├── .github/
│   └── workflows/
│       ├── check-and-publish.yml    # Scheduled routine for testing nodes and updating repo
│       └── release.yml              # Automated pipeline for building cross-platform binaries
├── test_probe.go                    # Utility script for manually verifying probe functions
├── test_race.sh                     # Shell script for running Go race detector tests
├── go.mod                           # Go module dependencies
└── README.md                        # Project documentation
```

## 🤝 Contributing

Contributions are always welcome! Feel free to open an issue or submit a Pull Request if you have suggestions for improvements, new features, or bug fixes.

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
