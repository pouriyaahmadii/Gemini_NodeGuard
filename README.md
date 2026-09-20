<div align="center">
  <h1>🛡️ Gemini NodeGuard</h1>
  <p><b>A high-performance, privacy-focused proxy subscription tester and classifier designed to verify node compatibility with Google Gemini and Google AI Studio services.</b></p>
  
  [![Go Version](https://img.shields.io/badge/Go-1.20+-00ADD8?logo=go&logoColor=white)](https://golang.org/)
  [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
  [![CI Build](https://img.shields.io/badge/build-passing-brightgreen?logo=github-actions)](https://github.com/pouriyaahmadii/gemini-nodeguard/actions)
  [![Platform](https://img.shields.io/badge/platform-Linux%20%7C%20macOS%20%7C%20Windows-lightgrey)](#)
</div>

---

## 📖 Overview

**Gemini NodeGuard** is an automated Go utility designed for users who need reliable and uninterrupted connectivity to Google AI services. It automatically fetches your private proxy subscription links, parses the nodes, and actively probes them to ensure they can connect to Google AI endpoints without hitting annoying geoblocks or authentication false negatives. 

## ✨ Key Features & Architecture

- 🚀 **Dual-Engine Core Support**: Seamless fallback between `sing-box` and `Xray` cores for maximum protocol coverage, including VLESS, VMess, Reality, Shadowsocks, and Trojan.
- 🎯 **Dedicated Gemini Probing**: Validates connectivity against `gemini.google.com`, `jules.google.com`, and `generativelanguage.googleapis.com` with smart status-code handling to eliminate unauthenticated false negatives.
- 📦 **Segmented Plain-Text Outputs**: Exports nodes as clean, readable `vless://...` line-by-line configurations in:
  - `output/gemini-sub.txt`: Nodes verified to unlock Gemini and Google AI.
  - `output/general-sub.txt`: General alive nodes for everyday web browsing.
- 🔒 **Privacy-First Design**: No proxies or subscription credentials are ever committed to public git branches.

---

## ☁️ Step-by-Step User Guide (Fork & Deploy)

You can run your own private, automated checker entirely through GitHub Actions without downloading any code locally.

### Step 1: Fork this repository
Click the **Fork** button at the top right of this page to create your own copy of the project.

### Step 2: Configure Subscription Links
1. Go to your forked repository's **Settings > Secrets and variables > Actions**.
2. Click **New repository secret**.
3. Name it `SUB_URLS`.
4. Paste your private subscription links (comma-separated if you have multiple) into the value field.

### Step 3: Run the Workflow
1. Navigate to the **Actions** tab in your repository.
2. Select the testing workflow on the left sidebar.
3. Click the **Run workflow** dropdown on the right and trigger it.

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

---

## 💻 Local Usage

If you prefer to run the application on your own machine:

### Installation
Make sure you have Go installed, then clone and build the binary:
```bash
git clone https://github.com/pouriyaahmadii/gemini-nodeguard.git
cd gemini-nodeguard
go build -o gemini-nodeguard cmd/checker/main.go
```

### CLI Arguments
You can customize the execution using command-line flags:

| Flag | Description | Default |
| :--- | :--- | :--- |
| `-sub` | Raw subscription URL (comma-separated) or path to an input file. | `""` |
| `-config` | Path to a `config.json` configuration file. | `""` |
| `-singbox` | Custom path to the `sing-box` binary. | Auto-detected |
| `-xray` | Custom path to the `xray` binary. | Auto-detected |
| `-concurrency`| Number of concurrent worker threads for testing nodes. | `10` |
| `-timeout` | Timeout duration for each node probe (e.g., `8s`). | `8s` |

**Example Execution:**
```bash
./gemini-nodeguard -sub "https://your-private-sub.com" -concurrency 20 -timeout 5s
```

---

## 🔒 Security & Disclaimer

**Privacy First:** All raw inputs and checked outputs are completely isolated per fork. Your node links and configurations are processed on secure, ephemeral GitHub Runners and are **never** transmitted to any external third parties, servers, or central tracking databases. 

*Use this tool responsibly. The authors are not responsible for how the proxy nodes are utilized by the end-user.*

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
